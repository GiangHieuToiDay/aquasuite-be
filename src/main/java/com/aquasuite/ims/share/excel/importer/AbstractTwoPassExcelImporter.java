package com.aquasuite.ims.share.excel.importer;

import com.aquasuite.ims.share.excel.dto.ExcelRowError;
import com.aquasuite.ims.share.excel.exception.ExcelErrorCode;
import com.aquasuite.ims.share.excel.storage.TempExcelFileManager;
import com.aquasuite.ims.share.excel.tracker.RedisImportProgressTracker;
import com.aquasuite.ims.share.exception.AppException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractTwoPassExcelImporter<T, C> {

    private static final int DEFAULT_BATCH_SIZE = 500;

    @PersistenceContext protected EntityManager entityManager;

    protected final RedisImportProgressTracker progressTracker;
    protected final TempExcelFileManager tempFileManager;
    protected final PlatformTransactionManager transactionManager;

    /** Index dòng chứa Header (1-based theo FastExcel: Dòng 2 trong Excel -> trả về 2) */
    protected abstract int getHeaderRowNumber();

    /** Index dòng bắt đầu dữ liệu (1-based: Dòng 3 trong Excel -> trả về 3) */
    protected abstract int getDataStartRowNumber();

    /** Danh sách tên các cột tiêu đề chuẩn để kiểm tra ở Pass 1 */
    protected abstract List<String> getExpectedHeaders();

    /** Load sẵn từ điển tra cứu (VD: Map Category, Map Brand) 1 lần duy nhất trước Pass 2 */
    protected abstract C prepareLookupContext();

    /** Đọc 1 dòng Excel thành DTO <T>. Nếu lỗi cú pháp ô thì ném IllegalArgumentException */
    protected abstract T mapRowToDto(Row row, int rowNumber);

    /**
     * Xử lý nghiệp vụ cho 1 mẻ 100 dòng:
     * - Validate với LookupContext <C>
     * - Gọi entityManager.persist() hoặc entityManager.merge()
     * - Đẩy các dòng lỗi vào list batchErrors
     * @return Số lượng dòng đã lưu thành công trong mẻ này
     */
    protected abstract int processAndPersistBatch(
            List<T> batch, C lookupContext, List<ExcelRowError> batchErrors);

    /** Hook chạy sau khi toàn bộ các mẻ đã hoàn tất (VD: Bắn Event sang Inventory + Ghi Audit Log) */
    protected void onImportCompleted(
            String jobId, int totalSuccess, int totalErrors, C lookupContext) {
        // Optional hook cho module con override
    }

    public final void executeImport(File tempExcelFile, String jobId) {
        try {
            // PASS 1: QUÉT LƯỚT ĐẾM DÒNG & CHECK HEADER (KHÔNG LƯU DỮ LIỆU VÀO RAM)
            int totalDataRows = runPass1ValidateHeaderAndCountRows(tempExcelFile);
            if (totalDataRows == 0) {
                throw new AppException(ExcelErrorCode.NO_DATA_ROWS);
            }

            // Khởi tạo Redis: tổng_dòng = totalDataRows, đã_xử_lý = 0 (0%)
            progressTracker.initJob(jobId, totalDataRows);

            // PASS 2: STREAM ĐỌC -> GOM BATCH 100 -> LƯU DB -> UPDATE REDIS -> XÓA RAM
            runPass2StreamAndBatchPersist(tempExcelFile, jobId);

        } catch (Exception ex) {
            log.error("Import job {} failed: ", jobId, ex);
            progressTracker.markFailed(jobId, ex.getMessage());
            throw ex;
        } finally {
            // Luôn xóa file tạm sau khi kết thúc
            tempFileManager.deleteQuietly(tempExcelFile);
        }
    }

    private int runPass1ValidateHeaderAndCountRows(File file) {
        try (ReadableWorkbook wb = new ReadableWorkbook(file)) {
            Sheet sheet = wb.getFirstSheet();
            int count = 0;
            try (Stream<Row> rows = sheet.openStream()) {
                var iterator = rows.iterator();
                while (iterator.hasNext()) {
                    Row row = iterator.next();
                    int rowNum = row.getRowNum(); // 1-based

                    // 1. Kiểm tra dòng tiêu đề (VD: Dòng 2)
                    if (rowNum == getHeaderRowNumber()) {
                        validateHeaders(row);
                    }
                    // 2. Đếm các dòng dữ liệu không rỗng (Từ Dòng 3 trở đi)
                    else if (rowNum >= getDataStartRowNumber() && !isRowEmpty(row)) {
                        count++;
                    }
                }
            }
            return count;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ExcelErrorCode.FILE_READ_ERROR, e.getMessage());
        }
    }

    private void runPass2StreamAndBatchPersist(File file, String jobId) {
        // Chuẩn bị bộ từ điển (Category Map, Brand Map) 1 lần duy nhất
        C lookupContext = prepareLookupContext();

        // Cấu hình Transaction riêng biệt (REQUIRES_NEW) cho từng mẻ 100 dòng
        TransactionTemplate batchTx = new TransactionTemplate(transactionManager);
        batchTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        List<T> currentBatch = new ArrayList<>(getBatchSize());
        List<ExcelRowError> currentBatchErrors = new ArrayList<>();

        try (ReadableWorkbook wb = new ReadableWorkbook(file);
                Stream<Row> rows = wb.getFirstSheet().openStream()) {

            rows.forEach(
                    row -> {
                        int rowNum = row.getRowNum();
                        if (rowNum < getDataStartRowNumber() || isRowEmpty(row)) {
                            return;
                        }

                        // Bước 1: Đọc dòng vào Batch
                        try {
                            T dto = mapRowToDto(row, rowNum);
                            currentBatch.add(dto);
                        } catch (Exception mapEx) {
                            currentBatchErrors.add(
                                    new ExcelRowError(rowNum, "N/A", mapEx.getMessage()));
                        }

                        // Khi đủ 100 dòng -> Thực thi chu kỳ: Ghi DB -> Update Redis -> Xóa RAM
                        if ((currentBatch.size() + currentBatchErrors.size()) >= getBatchSize()) {
                            flushSingleBatchCycle(
                                    batchTx,
                                    currentBatch,
                                    currentBatchErrors,
                                    lookupContext,
                                    jobId);
                        }
                    });

            // Xử lý nốt mẻ lẻ cuối cùng (nếu còn dư < 100 dòng)
            if (!currentBatch.isEmpty() || !currentBatchErrors.isEmpty()) {
                flushSingleBatchCycle(
                        batchTx, currentBatch, currentBatchErrors, lookupContext, jobId);
            }
        } catch (Exception e) {
            throw new AppException(ExcelErrorCode.FILE_READ_ERROR, e.getMessage());
        }

        // Kết thúc 100%: Gọi hook bắn Event sang module khác & cập nhật MSG42
        var finalState = progressTracker.getProgress(jobId);
        onImportCompleted(
                jobId, finalState.getSuccessRows(), finalState.getErrorRows(), lookupContext);
        progressTracker.markCompleted(
                jobId,
                String.format(
                        "Import completed successfully: %d created/updated, %d errors.",
                        finalState.getSuccessRows(), finalState.getErrorRows()));
    }

    private void flushSingleBatchCycle(
            TransactionTemplate batchTx,
            List<T> currentBatch,
            List<ExcelRowError> currentBatchErrors,
            C lookupContext,
            String jobId) {

        int totalInBatch = currentBatch.size() + currentBatchErrors.size();

        // --- BƯỚC 2: GHI DB NGAY TRONG 1 TRANSACTION RIÊNG ---
        Integer savedCount =
                batchTx.execute(
                        status -> {
                            if (currentBatch.isEmpty()) return 0;

                            // 2a. Module con gọi entityManager.persist() / merge()
                            int count =
                                    processAndPersistBatch(
                                            currentBatch, lookupContext, currentBatchErrors);

                            // 2b. Ép Hibernate đẩy toàn bộ 100 câu lệnh SQL Batch xuống PostgreSQL
                            // ngay lập tức
                            entityManager.flush();

                            // --- BƯỚC 4 (PHẦN 1): XÓA RAM CỦA HIBERNATE PERSISTENCE CONTEXT ---
                            entityManager.clear();

                            return count;
                        });

        // --- BƯỚC 3: UPDATE PROGRESS LÊN REDIS (4% -> 8% -> ...) ---
        progressTracker.updateBatchProgress(
                jobId, totalInBatch, savedCount != null ? savedCount : 0, currentBatchErrors);

        // --- BƯỚC 4 (PHẦN 2): XÓA SẠCH RAM CỦA JAVA LIST ĐỂ ĐÓN MẺ TIẾP THEO ---
        currentBatch.clear();
        currentBatchErrors.clear();
    }

    private void validateHeaders(Row headerRow) {
        List<String> expected = getExpectedHeaders();
        for (int i = 0; i < expected.size(); i++) {
            String actualCell = headerRow.getCellText(i).trim();
            if (!expected.get(i).equalsIgnoreCase(actualCell)) {
                throw new AppException(
                        ExcelErrorCode.INVALID_HEADER_SCHEMA,
                        getHeaderRowNumber(),
                        "Cột "
                                + (i + 1)
                                + " mong đợi '"
                                + expected.get(i)
                                + "' nhưng nhận được '"
                                + actualCell
                                + "'");
            }
        }
    }

    protected boolean isRowEmpty(Row row) {
        if (row == null || row.getCellCount() == 0) return true;
        for (int i = 0; i < row.getCellCount(); i++) {
            String text = row.getCellText(i);
            if (text != null && !text.isBlank()) return false;
        }
        return true;
    }

    protected int getBatchSize() {
        return DEFAULT_BATCH_SIZE; // Mặc định 100 dòng / mẻ
    }
}
