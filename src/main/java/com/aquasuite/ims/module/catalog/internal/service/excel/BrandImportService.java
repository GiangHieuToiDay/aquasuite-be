package com.aquasuite.ims.module.catalog.internal.service.excel;

import com.aquasuite.ims.module.catalog.internal.error.CatalogConstraintExceptionTranslator;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import com.aquasuite.ims.share.excel.storage.TempExcelFileManager;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class BrandImportService {
    private static final String PREFIX = "aquasuite:brand:import:";
    private static final Duration TTL = Duration.ofHours(1);
    private static final long MAX_BYTES = 5_242_880L;
    private static final List<String> HEADERS = List.of("code", "name", "description", "status");
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_]*$");
    private final RedisTemplate<String, Object> redis;
    private final TempExcelFileManager tempFiles;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final IamApi iam;
    private final Executor taskExecutor;

    public BrandImportJob submit(MultipartFile upload) {
        if (upload == null || upload.isEmpty() || upload.getOriginalFilename() == null
                || !upload.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        if (upload.getSize() > MAX_BYTES) throw new AppException(BrandImportError.FILE_TOO_LARGE);
        File file;
        try { file = tempFiles.saveToTempFile(upload, "brand"); }
        catch (Exception ex) { throw new AppException(BrandImportError.UNAVAILABLE); }
        BrandImportJob job = new BrandImportJob(UUID.randomUUID(), iam.getCurrentUserId());
        try { save(job); }
        catch (Exception ex) {
            tempFiles.deleteQuietly(file);
            throw new AppException(BrandImportError.UNAVAILABLE);
        }
        try { taskExecutor.execute(() -> run(file, job)); }
        catch (RuntimeException ex) {
            tempFiles.deleteQuietly(file);
            failBestEffort(job, "PROCESSING_FAILED");
        }
        return job;
    }

    public BrandImportJob get(UUID id) {
        BrandImportJob job = read(id);
        if (!job.ownerId.equals(iam.getCurrentUserId()))
            throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND);
        return job;
    }

    private BrandImportJob read(UUID id) {
        try {
            Object value = redis.opsForValue().get(PREFIX + id);
            if (value == null) throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND);
            return (BrandImportJob) value;
        } catch (AppException ex) { throw ex; }
        catch (Exception ex) { throw new AppException(BrandImportError.UNAVAILABLE); }
    }

    private void save(BrandImportJob job) {
        redis.opsForValue().set(PREFIX + job.jobId, job, TTL);
    }

    private void run(File file, BrandImportJob job) {
        try {
            job.status = "PROCESSING";
            save(job);
            int count = scan(file);
            job.totalRows = count;
            job.percentage = 0;
            save(job);
            process(file, job);
            job.status = job.errorRows + job.skippedRows > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED";
            job.percentage = 100;
            job.message = job.status;
            job.finishedAt = Instant.now();
            save(job);
        } catch (JobFailure ex) { failBestEffort(job, ex.reason); }
        catch (Exception ex) { failBestEffort(job, "PROCESSING_FAILED"); }
        finally { tempFiles.deleteQuietly(file); }
    }

    private void failBestEffort(BrandImportJob job, String reason) {
        job.status = "FAILED";
        job.jobReason = reason;
        job.message = reason;
        job.finishedAt = Instant.now();
        try { save(job); } catch (Exception ignored) { /* Redis outage is an accepted limitation. */ }
    }

    int scan(File file) {
        try (ReadableWorkbook workbook = new ReadableWorkbook(file);
             Stream<Row> rows = workbook.getFirstSheet().openStream()) {
            boolean header = false;
            int count = 0;
            for (Row row : (Iterable<Row>) rows::iterator) {
                if (row.getRowNum() == 1) {
                    header = true;
                    for (int i = 0; i < 4; i++)
                        if (!HEADERS.get(i).equalsIgnoreCase(row.getCellText(i).trim()))
                            throw new JobFailure("INVALID_HEADER");
                    for (int i = 4; i < row.getCellCount(); i++)
                        if (!row.getCellText(i).isBlank()) throw new JobFailure("INVALID_HEADER");
                } else if (row.getRowNum() > 1 && !empty(row)) {
                    if (++count > 10_000) throw new JobFailure("ROW_LIMIT");
                }
            }
            if (!header) throw new JobFailure("INVALID_HEADER");
            if (count == 0) throw new JobFailure("EMPTY_DATA");
            return count;
        } catch (JobFailure ex) { throw ex; }
        catch (Exception ex) { throw new JobFailure("INVALID_FILE"); }
    }

    private void process(File file, BrandImportJob job) {
        Map<String, Integer> codes = new HashMap<>(), names = new HashMap<>();
        try (ReadableWorkbook workbook = new ReadableWorkbook(file);
             Stream<Row> rows = workbook.getFirstSheet().openStream()) {
            for (Row row : (Iterable<Row>) rows::iterator) {
                if (row.getRowNum() < 2 || empty(row)) continue;
                try { handle(row, job, codes, names); }
                catch (JobFailure ex) { throw ex; }
                catch (Exception ex) { throw new JobFailure("PROCESSING_FAILED"); }
            }
        } catch (JobFailure ex) { throw ex; }
        catch (Exception ex) { throw new JobFailure("INVALID_FILE"); }
    }

    private void handle(Row row, BrandImportJob job, Map<String,Integer> codes, Map<String,Integer> names) {
        String[] raw = new String[4];
        List<String> reasons = new ArrayList<>();
        boolean invalidCell = false;
        boolean extraCell = false;
        try {
            for (int i = 0; i < 4; i++) {
                Cell cell = row.getOptionalCell(i).orElse(null);
                if (cell != null && cell.getType() != CellType.EMPTY &&
                        (cell.getType() != CellType.STRING || cell.getFormula() != null)) invalidCell = true;
                raw[i] = cell == null ? "" : cell.getText();
            }
            for (int i = 4; i < row.getCellCount(); i++)
                if (!row.getCellText(i).isBlank()) { extraCell = true; break; }
        } catch (Exception ex) { throw new JobFailure("INVALID_FILE"); }
        String code = raw[0] == null ? "" : raw[0].trim().toUpperCase(Locale.ROOT);
        String name = raw[1] == null ? "" : raw[1].trim();
        String desc = raw[2] == null ? "" : raw[2].trim();
        String status = raw[3] == null ? "" : raw[3].trim();
        if (code.isEmpty() || code.length() > 50 || !CODE.matcher(code).matches()) reasons.add("INVALID_CODE");
        if (raw[1] == null || raw[1].isBlank() || raw[1].length() > 100 || name.length() > 100) reasons.add("INVALID_NAME");
        if (raw[2] != null && (raw[2].length() > 5000 || desc.length() > 5000)) reasons.add("INVALID_DESCRIPTION");
        if (!status.isEmpty() && !status.equals("ACTIVE") && !status.equals("INACTIVE")) reasons.add("INVALID_STATUS");
        if (invalidCell) reasons.add("INVALID_CELL");
        if (extraCell) reasons.add("EXTRA_CELL");
        if (!reasons.isEmpty()) { record(job, row.getRowNum(), code, "ERROR", reasons, null, null); return; }
        if (status.isEmpty()) status = "ACTIVE";
        String codeKey = key(code), nameKey = key(name);
        Integer codeRow = codes.putIfAbsent(codeKey, row.getRowNum());
        Integer nameRow = names.putIfAbsent(nameKey, row.getRowNum());
        List<String> duplicate = new ArrayList<>();
        if (codeRow != null) duplicate.add("FILE_DUPLICATE_CODE");
        if (nameRow != null) duplicate.add("FILE_DUPLICATE_NAME");
        if (!duplicate.isEmpty()) { record(job, row.getRowNum(), code, "SKIPPED", duplicate, codeRow, nameRow); return; }
        if (exists("brand_code", code)) duplicate.add("DB_DUPLICATE_CODE");
        if (exists("brand_name", name)) duplicate.add("DB_DUPLICATE_NAME");
        if (!duplicate.isEmpty()) { record(job, row.getRowNum(), code, "SKIPPED", duplicate, null, null); return; }
        TransactionTemplate tx = new TransactionTemplate(transactions);
        try {
            String finalStatus = status;
            tx.executeWithoutResult(ignored -> jdbc.update(
                    "insert into public.brands(brand_id, brand_code, brand_name, description, status) values (?,?,?,?,?)",
                    UUID.randomUUID(), code, name, desc.isEmpty() ? null : desc, finalStatus));
        } catch (DataAccessException ex) {
            String constraint = CatalogConstraintExceptionTranslator.extractConstraintName(ex);
            if ("uq_brands_code_lower".equals(constraint)) duplicate.add("DB_DUPLICATE_CODE");
            else if ("uq_brands_name_lower".equals(constraint)) duplicate.add("DB_DUPLICATE_NAME");
            else throw new JobFailure("PROCESSING_FAILED");
            record(job, row.getRowNum(), code, "SKIPPED", duplicate, null, null);
            return;
        }
        job.createdRows++;
        job.processedRows++;
        progress(job);
    }

    private String key(String value) {
        try { return jdbc.queryForObject("select lower(btrim(?))", String.class, value); }
        catch (Exception ex) { throw new JobFailure("PROCESSING_FAILED"); }
    }
    private boolean exists(String column, String value) {
        try { return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from public.brands where lower(btrim(" + column + ")) = lower(btrim(?)))",
                Boolean.class, value)); }
        catch (Exception ex) { throw new JobFailure("PROCESSING_FAILED"); }
    }
    private void record(BrandImportJob job, int row, String code, String result, List<String> reasons,
                        Integer codeRow, Integer nameRow) {
        job.rows.add(new BrandImportJob.BrandImportRow(row, code, result, String.join(",", reasons), codeRow, nameRow));
        job.processedRows++;
        if (result.equals("ERROR")) job.errorRows++; else job.skippedRows++;
        progress(job);
    }
    private void progress(BrandImportJob job) {
        job.percentage = job.totalRows == null ? null : 100 * job.processedRows / job.totalRows;
        job.message = "PROCESSING";
        try { save(job); } catch (Exception ex) { throw new JobFailure("PROCESSING_FAILED"); }
    }
    private boolean empty(Row row) {
        for (int i = 0; i < row.getCellCount(); i++)
            if (!row.getCellText(i).isBlank()) return false;
        return true;
    }

    public byte[] template() { return workbook(null); }
    public byte[] report(UUID id) {
        BrandImportJob job = get(id);
        if (!Set.of("FAILED", "COMPLETED", "COMPLETED_WITH_ERRORS").contains(job.status))
            throw new AppException(BrandImportError.REPORT_NOT_READY);
        return workbook(job);
    }
    private byte[] workbook(BrandImportJob job) {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (job == null) {
                org.apache.poi.ss.usermodel.Row header = book.createSheet("Brands").createRow(0);
                for (int i = 0; i < HEADERS.size(); i++) header.createCell(i).setCellValue(HEADERS.get(i));
            } else {
                Sheet summary = book.createSheet("Summary");
                String[] keys = {"jobId", "status", "jobReason", "totalRows", "processedRows", "createdRows", "skippedRows", "errorRows"};
                Object[] vals = {job.jobId, job.status, job.jobReason, job.totalRows, job.processedRows, job.createdRows, job.skippedRows, job.errorRows};
                for (int i = 0; i < keys.length; i++) { org.apache.poi.ss.usermodel.Row r = summary.createRow(i); r.createCell(0).setCellValue(keys[i]); text(r.createCell(1), vals[i]); }
                Sheet details = book.createSheet("Rows");
                String[] cols = {"rowNumber", "code", "result", "reasonCodes", "firstDuplicateCodeRow", "firstDuplicateNameRow"};
                org.apache.poi.ss.usermodel.Row h = details.createRow(0);
                for (int i = 0; i < cols.length; i++) h.createCell(i).setCellValue(cols[i]);
                for (int i = 0; i < job.rows.size(); i++) {
                    var item = job.rows.get(i); var r = details.createRow(i + 1);
                    Object[] values = {item.rowNumber(), item.code(), item.result(), item.reasonCodes(), item.firstDuplicateCodeRow(), item.firstDuplicateNameRow()};
                    for (int j = 0; j < values.length; j++) text(r.createCell(j), values[j]);
                }
            }
            book.write(out); return out.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException(ex); }
    }
    private void text(org.apache.poi.ss.usermodel.Cell cell, Object value) {
        if (value != null) { cell.setCellType(org.apache.poi.ss.usermodel.CellType.STRING); cell.setCellValue(value.toString()); }
    }
    private static final class JobFailure extends RuntimeException {
        final String reason;
        JobFailure(String reason) { this.reason = reason; }
    }
}
