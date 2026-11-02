package com.aquasuite.ims.module.catalog.internal.service.excel;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.excel.storage.TempExcelFileManager;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BrandImportServiceTest {
    @Test
    void savesOwnerBeforeDispatchAndRejectsCrossOwner() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        IamApi iam = mock(IamApi.class);
        UUID owner = UUID.randomUUID();
        when(iam.getCurrentUserId()).thenReturn(owner, UUID.randomUUID());
        Executor executor = command -> {
            verify(values).set(startsWith("aquasuite:brand:import:"), any(BrandImportJob.class), any());
        };
        TempExcelFileManager temp = mock(TempExcelFileManager.class);
        when(temp.saveToTempFile(any(), eq("brand"))).thenReturn(new File("unused-test-file.xlsx"));
        BrandImportService service = new BrandImportService(redis, temp, mock(JdbcTemplate.class),
                mock(PlatformTransactionManager.class), iam, executor);
        var upload = new MockMultipartFile("file", "brands.xlsx", "application/octet-stream", new byte[]{1});
        var job = service.submit(upload);
        assertEquals(owner, job.ownerId);
        when(values.get("aquasuite:brand:import:" + job.jobId)).thenReturn(job);
        assertThrows(AppException.class, () -> service.get(job.jobId));
    }

    @Test
    void rejectsOversizeBeforeStaging() {
        BrandImportService service = new BrandImportService(mock(RedisTemplate.class), mock(TempExcelFileManager.class),
                mock(JdbcTemplate.class), mock(PlatformTransactionManager.class), mock(IamApi.class), mock(Executor.class));
        MockMultipartFile upload = new MockMultipartFile("file", "brands.xlsx", "application/octet-stream", new byte[5_242_881]);
        AppException error = assertThrows(AppException.class, () -> service.submit(upload));
        assertEquals("BRIMP_002", error.getErrorCode().getCode());
    }

    @Test
    void progressAndReportFailClosedWhenRedisCannotReadOwner() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new DataAccessResourceFailureException("redis unavailable"));
        BrandImportService service = new BrandImportService(redis, mock(TempExcelFileManager.class),
                mock(JdbcTemplate.class), mock(PlatformTransactionManager.class), mock(IamApi.class), mock(Executor.class));
        UUID id = UUID.randomUUID();
        assertEquals("BRIMP_003", assertThrows(AppException.class, () -> service.get(id)).getErrorCode().getCode());
        assertEquals("BRIMP_003", assertThrows(AppException.class, () -> service.report(id)).getErrorCode().getCode());
    }

    @Test
    void reportRequiresTerminalStateAndWritesUntrustedCodeAsText() throws Exception {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        IamApi iam = mock(IamApi.class);
        UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
        when(iam.getCurrentUserId()).thenReturn(owner);
        BrandImportJob job = new BrandImportJob(id, owner);
        when(values.get("aquasuite:brand:import:" + id)).thenReturn(job);
        BrandImportService service = new BrandImportService(redis, mock(TempExcelFileManager.class),
                mock(JdbcTemplate.class), mock(PlatformTransactionManager.class), iam, mock(Executor.class));
        assertEquals("BRIMP_001", assertThrows(AppException.class, () -> service.report(id)).getErrorCode().getCode());
        job.status = "FAILED";
        job.rows.add(new BrandImportJob.BrandImportRow(2, "=2+2", "ERROR", "INVALID_CODE", null, null));
        try (var book = new XSSFWorkbook(new java.io.ByteArrayInputStream(service.report(id)))) {
            assertEquals(org.apache.poi.ss.usermodel.CellType.STRING, book.getSheet("Rows").getRow(1).getCell(1).getCellType());
            assertEquals("=2+2", book.getSheet("Rows").getRow(1).getCell(1).getStringCellValue());
        }
    }

    @Test
    void scanRejectsExtraHeaderAndDoesNotCountBlankRows() throws Exception {
        BrandImportService service = new BrandImportService(mock(RedisTemplate.class), mock(TempExcelFileManager.class),
                mock(JdbcTemplate.class), mock(PlatformTransactionManager.class), mock(IamApi.class), mock(Executor.class));
        File file = Files.createTempFile("brand-scan-test", ".xlsx").toFile();
        try {
            try (var book = new XSSFWorkbook(); var out = Files.newOutputStream(file.toPath())) {
                var sheet = book.createSheet();
                var header = sheet.createRow(0);
                String[] names = {"code", "name", "description", "status"};
                for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
                sheet.createRow(1).createCell(0).setCellValue("B001");
                sheet.createRow(2);
                book.write(out);
            }
            assertEquals(1, service.scan(file));
            try (var book = new XSSFWorkbook()) {
                var header = book.createSheet().createRow(0);
                String[] names = {"code", "name", "description", "status", "extra"};
                for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
                try (var out = Files.newOutputStream(file.toPath())) { book.write(out); }
            }
            assertThrows(RuntimeException.class, () -> service.scan(file));
        } finally { Files.deleteIfExists(file.toPath()); }
    }

    @Test
    void malformedWorkbookIsAcceptedAsJobThenFailsWithoutDatabaseWrites() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        AtomicReference<BrandImportJob> saved = new AtomicReference<>();
        doAnswer(invocation -> {
            saved.set(invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), any(), any());
        IamApi iam = mock(IamApi.class);
        when(iam.getCurrentUserId()).thenReturn(UUID.randomUUID());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BrandImportService service = new BrandImportService(redis, new TempExcelFileManager(), jdbc,
                mock(PlatformTransactionManager.class), iam, Runnable::run);
        var upload = new MockMultipartFile("file", "bad.xlsx", "application/octet-stream", new byte[]{1, 2, 3});
        var job = service.submit(upload);
        assertEquals("FAILED", saved.get().status);
        assertEquals("INVALID_FILE", saved.get().jobReason);
        verifyNoInteractions(jdbc);
    }

    @Test
    void secondPassReadFailureIsInvalidFile() throws Exception {
        runValidWorkbookWithFailure("INVALID_FILE", false, true, false, false);
    }

    @Test
    void databaseFailureIsProcessingFailed() throws Exception {
        runValidWorkbookWithFailure("PROCESSING_FAILED", true, false, false, false);
    }

    @Test
    void redisFailureDuringWorkerIsProcessingFailedWhenRedisRecovers() throws Exception {
        runValidWorkbookWithFailure("PROCESSING_FAILED", false, false, true, false);
    }

    @Test
    void unknownSystemFailureDefaultsToProcessingFailed() throws Exception {
        runValidWorkbookWithFailure("PROCESSING_FAILED", false, false, false, true);
    }

    private void runValidWorkbookWithFailure(String expected, boolean database, boolean corruptPass2,
                                             boolean redisFailure, boolean unknownFailure) throws Exception {
        File file = Files.createTempFile("brand-failure-test", ".xlsx").toFile();
        try {
            try (var book = new XSSFWorkbook(); var out = Files.newOutputStream(file.toPath())) {
                var sheet = book.createSheet();
                var header = sheet.createRow(0);
                String[] names = {"code", "name", "description", "status"};
                for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
                var data = sheet.createRow(1);
                data.createCell(0).setCellValue("B001"); data.createCell(1).setCellValue("Brand One");
                book.write(out);
            }
            TempExcelFileManager temp = mock(TempExcelFileManager.class);
            when(temp.saveToTempFile(any(), eq("brand"))).thenReturn(file);
            RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
            ValueOperations<String, Object> values = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(values);
            AtomicReference<BrandImportJob> state = new AtomicReference<>();
            AtomicInteger writes = new AtomicInteger();
            doAnswer(call -> {
                BrandImportJob job = call.getArgument(1);
                int n = writes.incrementAndGet();
                if (corruptPass2 && n == 3) Files.write(file.toPath(), new byte[]{1, 2, 3});
                if (redisFailure && n == 2) throw new DataAccessResourceFailureException("redis unavailable");
                state.set(job);
                return null;
            }).when(values).set(anyString(), any(), any());
            IamApi iam = mock(IamApi.class);
            when(iam.getCurrentUserId()).thenReturn(UUID.randomUUID());
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            if (database) when(jdbc.queryForObject(anyString(), eq(String.class), any()))
                    .thenThrow(new DataAccessResourceFailureException("database unavailable"));
            if (unknownFailure) when(jdbc.queryForObject(anyString(), eq(String.class), any()))
                    .thenThrow(new IllegalStateException("unexpected"));
            BrandImportService service = new BrandImportService(redis, temp, jdbc,
                    mock(PlatformTransactionManager.class), iam, Runnable::run);
            service.submit(new MockMultipartFile("file", "brands.xlsx", "application/octet-stream", new byte[]{1}));
            assertEquals("FAILED", state.get().status);
            assertEquals(expected, state.get().jobReason);
        } finally { Files.deleteIfExists(file.toPath()); }
    }
}
