package com.aquasuite.ims.module.catalog.internal.service.excel;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.excel.storage.TempExcelFileManager;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import com.aquasuite.ims.module.catalog.internal.controller.BrandImportController;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class BrandImportPostgresIntegrationTest extends PostgreSqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tx;
    @Autowired DataSource dataSource;
    @Autowired BrandImportController controller;

    @Test
    void methodSecurityRechecksCreateAuthority() {
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("actor", "n/a", List.of()));
            assertThrows(AccessDeniedException.class, () -> controller.template());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("actor", "n/a",
                            List.of(new SimpleGrantedAuthority("CATALOG_BRAND_CREATE"))));
            assertEquals(200, controller.template().getStatusCode().value());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("actor", "n/a", List.of()));
            assertThrows(AccessDeniedException.class, () -> controller.template());
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void concurrentCreateOnlyJobsLeaveOneBrandAndCountOneCreate() throws Exception {
        String code = "W15" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String name = "Concurrent " + UUID.randomUUID();
        byte[] file = workbook(new String[]{code, name, "", "ACTIVE"});
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var first = fixture(jdbc, workers);
            var second = fixture(jdbc, workers);
            BrandImportJob a = first.service.submit(upload(file));
            BrandImportJob b = second.service.submit(upload(file));
            workers.shutdown();
            assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("select count(*) from public.brands where brand_code = ?", Integer.class, code));
            assertEquals(1, a.createdRows + b.createdRows);
            assertEquals(1, a.skippedRows + b.skippedRows);
            assertEquals(1, a.processedRows);
            assertEquals(1, b.processedRows);
        } finally {
            workers.shutdownNow();
            jdbc.update("delete from public.brands where brand_code = ?", code);
        }
    }

    @Test
    void rollbackOfSecondInsertDoesNotIncreaseCreatedCounter() throws Exception {
        String prefix = "W15" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        String firstCode = prefix + "A", secondCode = prefix + "B";
        String suffix = UUID.randomUUID().toString();
        AtomicInteger inserts = new AtomicInteger();
        JdbcTemplate failing = new JdbcTemplate(dataSource) {
            @Override public int update(String sql, Object... args) {
                int result = super.update(sql, args);
                if (sql.startsWith("insert into public.brands") && inserts.incrementAndGet() == 2)
                    throw new org.springframework.dao.DataAccessResourceFailureException("injected after insert");
                return result;
            }
        };
        var fixture = fixture(failing, Runnable::run);
        try {
            BrandImportJob job = fixture.service.submit(upload(workbook(
                    new String[]{firstCode, "First " + suffix, "", "ACTIVE"},
                    new String[]{secondCode, "Second " + suffix, "", "ACTIVE"})));
            assertEquals("FAILED", job.status);
            assertEquals("PROCESSING_FAILED", job.jobReason);
            assertEquals(1, job.createdRows);
            assertEquals(1, jdbc.queryForObject("select count(*) from public.brands where brand_code = ?", Integer.class, firstCode));
            assertEquals(0, jdbc.queryForObject("select count(*) from public.brands where brand_code = ?", Integer.class, secondCode));
        } finally {
            jdbc.update("delete from public.brands where brand_code in (?, ?)", firstCode, secondCode);
        }
    }

    @Test
    void inactiveDatabaseBrandAndFileDuplicateAreSkipped() throws Exception {
        String code = "W15" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String name = "Inactive " + UUID.randomUUID();
        jdbc.update("insert into public.brands(brand_id,brand_code,brand_name,status) values (?,?,?,?)",
                UUID.randomUUID(), code, name, "INACTIVE");
        try {
            var fixture = fixture(jdbc, Runnable::run);
            BrandImportJob job = fixture.service.submit(upload(workbook(
                    new String[]{code.toLowerCase(Locale.ROOT), "New " + UUID.randomUUID(), "", "ACTIVE"},
                    new String[]{code, "Another " + UUID.randomUUID(), "", "ACTIVE"})));
            assertEquals("COMPLETED_WITH_ERRORS", job.status);
            assertEquals(0, job.createdRows);
            assertEquals(2, job.skippedRows);
            assertEquals("DB_DUPLICATE_CODE", job.rows.get(0).reasonCodes());
            assertEquals("FILE_DUPLICATE_CODE", job.rows.get(1).reasonCodes());
            assertEquals(2, job.rows.get(1).firstDuplicateCodeRow());
        } finally { jdbc.update("delete from public.brands where brand_code = ?", code); }
    }

    @Test
    void redisFailureAfterCommitKeepsBrandWithoutReplayingIt() throws Exception {
        String code = "W15" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        AtomicInteger writes = new AtomicInteger();
        doAnswer(call -> {
            if (writes.incrementAndGet() == 4) throw new DataAccessResourceFailureException("injected Redis outage after commit");
            return null;
        }).when(values).set(anyString(), any(), any());
        IamApi iam = mock(IamApi.class);
        when(iam.getCurrentUserId()).thenReturn(UUID.randomUUID());
        BrandImportService service = new BrandImportService(redis, new TempExcelFileManager(), jdbc, tx, iam, Runnable::run);
        try {
            BrandImportJob job = service.submit(upload(workbook(new String[]{code, "Redis " + UUID.randomUUID(), "", "ACTIVE"})));
            assertEquals("FAILED", job.status);
            assertEquals("PROCESSING_FAILED", job.jobReason);
            assertEquals(1, jdbc.queryForObject("select count(*) from public.brands where brand_code = ?", Integer.class, code));
            assertEquals(5, writes.get());
        } finally { jdbc.update("delete from public.brands where brand_code = ?", code); }
    }

    @Test
    void twoIndependentFileDuplicateReferencesAndInvalidRowDoesNotReserveKeys() throws Exception {
        String prefix = "W15" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        String a = prefix + "A", b = prefix + "B";
        String suffix = UUID.randomUUID().toString();
        String x = " Name X " + suffix + " ", y = " Name Y " + suffix + " ";
        try {
            BrandImportJob job = fixture(jdbc, Runnable::run).service.submit(upload(workbook(
                    new String[]{"bad code", x, "", "ACTIVE"},
                    new String[]{a, x, "", "ACTIVE"},
                    new String[]{b, y, "", "ACTIVE"},
                    new String[]{a.toLowerCase(Locale.ROOT), y.trim(), "", "ACTIVE"})));
            assertEquals("COMPLETED_WITH_ERRORS", job.status);
            assertEquals(2, job.createdRows);
            assertEquals(1, job.errorRows);
            assertEquals(1, job.skippedRows);
            var duplicate = job.rows.get(1);
            assertEquals("FILE_DUPLICATE_CODE,FILE_DUPLICATE_NAME", duplicate.reasonCodes());
            assertEquals(3, duplicate.firstDuplicateCodeRow());
            assertEquals(4, duplicate.firstDuplicateNameRow());
        } finally { jdbc.update("delete from public.brands where brand_code in (?, ?)", a, b); }
    }

    @Test
    void forcedUniqueViolationAfterBothPrechecks() throws Exception {
        String code = "W15" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String name = "Race " + UUID.randomUUID();
        var barrier = new CyclicBarrier(2);
        var insertFailures = new AtomicInteger();
        JdbcTemplate synchronizedJdbc = new JdbcTemplate(dataSource) {
            @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
                T result = super.queryForObject(sql, type, args);
                if (sql.contains("lower(btrim(brand_name))")) {
                    try { barrier.await(10, TimeUnit.SECONDS); }
                    catch (Exception ex) { throw new RuntimeException(ex); }
                }
                return result;
            }
            @Override public int update(String sql, Object... args) {
                try { return super.update(sql, args); }
                catch (org.springframework.dao.DataAccessException ex) {
                    if (sql.startsWith("insert into public.brands")) insertFailures.incrementAndGet();
                    throw ex;
                }
            }
        };
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            byte[] file = workbook(new String[]{code, name, "", "ACTIVE"});
            var first = fixture(synchronizedJdbc, workers).service.submit(upload(file));
            var second = fixture(synchronizedJdbc, workers).service.submit(upload(file));
            workers.shutdown(); assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS));
            assertEquals(1, insertFailures.get());
            assertEquals(1, first.createdRows + second.createdRows);
            assertEquals(1, first.skippedRows + second.skippedRows);
            var skipped = first.skippedRows == 1 ? first : second;
            assertTrue(Set.of("DB_DUPLICATE_CODE", "DB_DUPLICATE_NAME").contains(skipped.rows.get(0).reasonCodes()));
            assertEquals(1, jdbc.queryForObject("select count(*) from public.brands where brand_code = ?", Integer.class, code));
        } finally { workers.shutdownNow(); jdbc.update("delete from public.brands where brand_code = ?", code); }
    }

    private Fixture fixture(JdbcTemplate database, Executor executor) {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        IamApi iam = mock(IamApi.class);
        when(iam.getCurrentUserId()).thenReturn(UUID.randomUUID());
        return new Fixture(new BrandImportService(redis, new TempExcelFileManager(), database, tx, iam, executor));
    }
    private record Fixture(BrandImportService service) {}

    private MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("file", "brands.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }
    private byte[] workbook(String[]... rows) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet();
            var header = sheet.createRow(0);
            String[] columns = {"code", "name", "description", "status"};
            for (int i = 0; i < 4; i++) header.createCell(i).setCellValue(columns[i]);
            for (int n = 0; n < rows.length; n++) {
                var row = sheet.createRow(n + 1);
                for (int i = 0; i < 4; i++) row.createCell(i).setCellValue(rows[n][i]);
            }
            book.write(out);
            return out.toByteArray();
        }
    }
}
