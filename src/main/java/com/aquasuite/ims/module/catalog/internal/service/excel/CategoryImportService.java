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
import org.springframework.transaction.TransactionDefinition;
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
public class CategoryImportService {
    private static final String PREFIX = "aquasuite:category:import:";
    private static final Duration TTL = Duration.ofHours(1);
    private static final long MAX_BYTES = 5_242_880L;
    private static final List<String> HEADERS = List.of("code", "name", "parent_code", "description", "status");
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_]*$");
    private final RedisTemplate<String, Object> redis;
    private final TempExcelFileManager tempFiles;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final IamApi iam;
    private final Executor taskExecutor;

    public CategoryImportJob submit(MultipartFile upload) {
        if (upload == null || upload.isEmpty() || upload.getOriginalFilename() == null
                || !upload.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        if (upload.getSize() > MAX_BYTES) throw new AppException(CategoryImportError.FILE_TOO_LARGE);
        UUID owner = iam.getCurrentUserId();
        File file;
        try { file = tempFiles.saveToTempFile(upload, "category"); }
        catch (Exception ex) { throw new AppException(CategoryImportError.UNAVAILABLE); }
        CategoryImportJob job = new CategoryImportJob(UUID.randomUUID(), owner);
        Map<String, UUID> snapshot;
        try {
            TransactionTemplate snapshotTx = new TransactionTemplate(transactions);
            snapshotTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            snapshot = snapshotTx.execute(ignored -> {
                lockHierarchy();
                Map<String, UUID> parents = new HashMap<>();
                jdbc.query("select category_code, category_id from public.categories", rs -> {
                    parents.put(rs.getString(1), rs.getObject(2, UUID.class));
                });
                return Map.copyOf(parents);
            });
            if (snapshot == null) throw new IllegalStateException("Snapshot unavailable");
            save(job);
        }
        catch (Exception ex) {
            tempFiles.deleteQuietly(file);
            throw new AppException(CategoryImportError.UNAVAILABLE);
        }
        try { taskExecutor.execute(() -> run(file, job, snapshot)); }
        catch (RuntimeException ex) {
            tempFiles.deleteQuietly(file);
            failBestEffort(job, "PROCESSING_FAILED");
        }
        return job;
    }

    public CategoryImportJob get(UUID id) {
        CategoryImportJob job = read(id);
        if (!job.ownerId.equals(iam.getCurrentUserId()))
            throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND);
        return job;
    }

    private CategoryImportJob read(UUID id) {
        try {
            Object value = redis.opsForValue().get(PREFIX + id);
            if (value == null) throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND);
            return (CategoryImportJob) value;
        } catch (AppException ex) { throw ex; }
        catch (Exception ex) { throw new AppException(CategoryImportError.UNAVAILABLE); }
    }

    private void save(CategoryImportJob job) {
        redis.opsForValue().set(PREFIX + job.jobId, job, TTL);
    }

    private void run(File file, CategoryImportJob job, Map<String, UUID> snapshot) {
        try {
            job.status = "PROCESSING";
            save(job);
            int count = scan(file);
            job.totalRows = count;
            job.percentage = 0;
            save(job);
            process(file, job, snapshot);
            job.status = job.errorRows + job.skippedRows > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED";
            job.percentage = 100;
            job.message = job.status;
            job.finishedAt = Instant.now();
            save(job);
        } catch (JobFailure ex) { failBestEffort(job, ex.reason); }
        catch (Exception ex) { failBestEffort(job, "PROCESSING_FAILED"); }
        finally { tempFiles.deleteQuietly(file); }
    }

    private void failBestEffort(CategoryImportJob job, String reason) {
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
                    for (int i = 0; i < HEADERS.size(); i++) {
                        String headerText = row.getOptionalCell(i).map(Cell::getText).orElse("");
                        if (!HEADERS.get(i).equalsIgnoreCase(headerText == null ? "" : headerText.trim()))
                            throw new JobFailure("INVALID_HEADER");
                    }
                    for (int i = HEADERS.size(); i < row.getCellCount(); i++)
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

    private void process(File file, CategoryImportJob job, Map<String, UUID> snapshot) {
        DuplicateTracker codes = new DuplicateTracker();
        Map<UUID, DuplicateTracker> names = new HashMap<>();
        try (ReadableWorkbook workbook = new ReadableWorkbook(file);
             Stream<Row> rows = workbook.getFirstSheet().openStream()) {
            for (Row row : (Iterable<Row>) rows::iterator) {
                if (row.getRowNum() < 2 || empty(row)) continue;
                try { handle(row, job, codes, names, snapshot); }
                catch (JobFailure ex) { throw ex; }
                catch (Exception ex) { throw new JobFailure("PROCESSING_FAILED"); }
            }
        } catch (JobFailure ex) { throw ex; }
        catch (Exception ex) { throw new JobFailure("INVALID_FILE"); }
    }

    private record NameRow(String name, int row) {}
    private record NameMatch(long hash, Integer row) {}
    private record Outcome(String result, List<String> reasons, Integer codeRow, Integer nameRow) {}

    private final class DuplicateTracker {
        private final Map<Long, List<NameRow>> buckets = new HashMap<>();

        private NameMatch find(String value) {
            Long hash = jdbc.queryForObject("select hashtextextended(lower(btrim(?)), 0)", Long.class, value);
            if (hash == null) throw new JobFailure("PROCESSING_FAILED");
            return new NameMatch(hash, firstMatchingName(buckets.getOrDefault(hash, List.of()), value));
        }
        private void reserve(NameMatch match, String value, int row) {
            if (match.row() != null) return;
            buckets.computeIfAbsent(match.hash(), unused -> new ArrayList<>()).add(new NameRow(value, row));
        }
    }

    private void lockHierarchy() {
        jdbc.execute("select pg_advisory_xact_lock(1128354892, 1)");
    }

    private void handle(Row row, CategoryImportJob job, DuplicateTracker codes,
                        Map<UUID,DuplicateTracker> names, Map<String,UUID> snapshot) {
        String[] raw = new String[5];
        List<String> reasons = new ArrayList<>();
        boolean invalidCell = false;
        try {
            for (int i = 0; i < raw.length; i++) {
                Cell cell = row.getOptionalCell(i).orElse(null);
                if (cell != null && cell.getType() != CellType.EMPTY &&
                        (cell.getType() != CellType.STRING || cell.getFormula() != null)) invalidCell = true;
                raw[i] = cell == null || cell.getText() == null ? "" : cell.getText();
            }
            for (int i = raw.length; i < row.getCellCount(); i++)
                if (!row.getCellText(i).isBlank()) { reasons.add("EXTRA_CELL"); break; }
        } catch (Exception ex) { throw new JobFailure("INVALID_FILE"); }
        String code = raw[0].trim().toUpperCase(Locale.ROOT);
        String name = raw[1].trim();
        String parentCode = raw[2].trim().toUpperCase(Locale.ROOT);
        String description = raw[3].trim();
        String status = raw[4].trim();
        if (code.isEmpty() || code.length() > 50 || !CODE.matcher(code).matches()) reasons.add("INVALID_CODE");
        if (raw[1].isBlank() || raw[1].length() > 100 || name.length() > 100) reasons.add("INVALID_NAME");
        if (raw[3].length() > 5000 || description.length() > 5000) reasons.add("INVALID_DESCRIPTION");
        if (!parentCode.isEmpty() && (parentCode.length() > 50 || !CODE.matcher(parentCode).matches())) reasons.add("INVALID_PARENT_CODE");
        if (!status.isEmpty() && !status.equals("ACTIVE") && !status.equals("INACTIVE")) reasons.add("INVALID_STATUS");
        if (invalidCell) reasons.add("INVALID_CELL");
        UUID parentId = parentCode.isEmpty() ? null : snapshot.get(parentCode);
        if (!parentCode.isEmpty() && parentId == null) reasons.add("PARENT_NOT_FOUND");
        if (!reasons.isEmpty()) { record(job, row.getRowNum(), code, parentCode, "ERROR", reasons, null, null); return; }
        final String finalStatus = status.isEmpty() ? "ACTIVE" : status;
        int rowNumber = row.getRowNum();
        TransactionTemplate tx = new TransactionTemplate(transactions);
        try {
            Outcome outcome = tx.execute(ignored -> {
                lockHierarchy();
                if (parentId != null && !validParent(parentId, parentCode))
                    return new Outcome("ERROR", List.of("PARENT_NOT_FOUND_OR_INVALID"), null, null);
                DuplicateTracker siblings = names.computeIfAbsent(parentId, unused -> new DuplicateTracker());
                NameMatch codeMatch = codes.find(code);
                NameMatch nameMatch = siblings.find(name);
                Integer firstCode = codeMatch.row();
                Integer firstName = nameMatch.row();
                codes.reserve(codeMatch, code, rowNumber);
                siblings.reserve(nameMatch, name, rowNumber);
                List<String> duplicates = new ArrayList<>();
                if (firstCode != null) duplicates.add("FILE_DUPLICATE_CODE");
                if (firstName != null) duplicates.add("FILE_DUPLICATE_NAME");
                if (!duplicates.isEmpty()) return new Outcome("SKIPPED", duplicates, firstCode, firstName);
                if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from public.categories where lower(btrim(category_code))=lower(btrim(?)))", Boolean.class, code))) duplicates.add("DB_DUPLICATE_CODE");
                if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from public.categories where parent_id is not distinct from cast(? as uuid) and lower(btrim(category_name))=lower(btrim(?)))", Boolean.class, parentId, name))) duplicates.add("DB_DUPLICATE_NAME");
                if (!duplicates.isEmpty()) return new Outcome("SKIPPED", duplicates, null, null);
                jdbc.update("insert into public.categories(category_id,category_code,category_name,parent_id,description,status) values (?,?,?,?,?,?)",
                        UUID.randomUUID(), code, name, parentId, description.isEmpty() ? null : description, finalStatus);
                return new Outcome("CREATED", List.of(), null, null);
            });
            if (outcome == null) throw new JobFailure("PROCESSING_FAILED");
            if ("CREATED".equals(outcome.result())) { job.createdRows++; job.processedRows++; progress(job); }
            else record(job, rowNumber, code, parentCode, outcome.result(), outcome.reasons(), outcome.codeRow(), outcome.nameRow());
        } catch (DataAccessException ex) {
            String constraint = CatalogConstraintExceptionTranslator.extractConstraintName(ex);
            String reason = switch (constraint == null ? "" : constraint) {
                case "uq_categories_code_lower" -> "DB_DUPLICATE_CODE";
                case "uq_categories_root_name_lower", "uq_categories_parent_name_lower" -> "DB_DUPLICATE_NAME";
                default -> null;
            };
            if (reason == null) throw new JobFailure("PROCESSING_FAILED");
            record(job, rowNumber, code, parentCode, "SKIPPED", List.of(reason), null, null);
        }
    }

    private boolean validParent(UUID id, String code) {
        Set<UUID> seen = new HashSet<>();
        UUID current = id;
        while (current != null) {
            if (!seen.add(current)) return false;
            List<Map<String,Object>> rows = jdbc.queryForList("select category_code, parent_id from public.categories where category_id=?", current);
            if (rows.size() != 1) return false;
            if (current.equals(id) && !code.equals(rows.get(0).get("category_code"))) return false;
            current = (UUID) rows.get(0).get("parent_id");
        }
        return true;
    }

    private Integer firstMatchingName(List<NameRow> names, String name) {
        if (names.isEmpty()) return null;
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) connection -> {
            try (java.sql.PreparedStatement stmt = connection.prepareStatement(
                    "select min(n) from unnest(cast(? as text[])) with ordinality t(v,n) where lower(btrim(v))=lower(btrim(?))")) {
                java.sql.Array array = connection.createArrayOf("text", names.stream().map(NameRow::name).toArray());
                try {
                    stmt.setArray(1, array); stmt.setString(2, name);
                    try (java.sql.ResultSet rs = stmt.executeQuery()) {
                        rs.next(); long index = rs.getLong(1);
                        return rs.wasNull() ? null : names.get((int) index - 1).row();
                    }
                } finally { array.free(); }
            }
        });
    }

    private void record(CategoryImportJob job, int row, String code, String parentCode, String result,
                        List<String> reasons, Integer codeRow, Integer nameRow) {
        job.rows.add(new CategoryImportJob.CategoryImportRow(row, code, parentCode, result, String.join(",", reasons), codeRow, nameRow));
        job.processedRows++;
        if (result.equals("ERROR")) job.errorRows++; else job.skippedRows++;
        progress(job);
    }
    private void progress(CategoryImportJob job) {
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
        CategoryImportJob job = get(id);
        if (!Set.of("FAILED", "COMPLETED", "COMPLETED_WITH_ERRORS").contains(job.status))
            throw new AppException(CategoryImportError.REPORT_NOT_READY);
        return workbook(job);
    }
    private byte[] workbook(CategoryImportJob job) {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (job == null) {
                org.apache.poi.ss.usermodel.Row header = book.createSheet("Categories").createRow(0);
                for (int i = 0; i < HEADERS.size(); i++) header.createCell(i).setCellValue(HEADERS.get(i));
            } else {
                Sheet summary = book.createSheet("Summary");
                String[] keys = {"jobId", "status", "jobReason", "totalRows", "processedRows", "createdRows", "skippedRows", "errorRows"};
                Object[] vals = {job.jobId, job.status, job.jobReason, job.totalRows, job.processedRows, job.createdRows, job.skippedRows, job.errorRows};
                for (int i = 0; i < keys.length; i++) { org.apache.poi.ss.usermodel.Row r = summary.createRow(i); r.createCell(0).setCellValue(keys[i]); text(r.createCell(1), vals[i]); }
                Sheet details = book.createSheet("Rows");
                String[] cols = {"rowNumber", "code", "parentCode", "result", "reasonCodes", "firstDuplicateCodeRow", "firstDuplicateNameRow"};
                org.apache.poi.ss.usermodel.Row h = details.createRow(0);
                for (int i = 0; i < cols.length; i++) h.createCell(i).setCellValue(cols[i]);
                for (int i = 0; i < job.rows.size(); i++) {
                    var item = job.rows.get(i); var r = details.createRow(i + 1);
                    Object[] values = {item.rowNumber(), item.code(), item.parentCode(), item.result(), item.reasonCodes(), item.firstCodeRow(), item.firstNameRow()};
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
