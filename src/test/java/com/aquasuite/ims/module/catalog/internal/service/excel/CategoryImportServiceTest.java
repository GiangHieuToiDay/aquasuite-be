package com.aquasuite.ims.module.catalog.internal.service.excel;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.excel.storage.TempExcelFileManager;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CategoryImportServiceTest {
    @Test void rejectsOversizeBeforeAnyStaging() {
        TempExcelFileManager temp = mock(TempExcelFileManager.class);
        CategoryImportService service = new CategoryImportService(mock(RedisTemplate.class), temp,
                mock(JdbcTemplate.class), mock(PlatformTransactionManager.class), mock(IamApi.class), mock(Executor.class));
        var file = new MockMultipartFile("file", "categories.xlsx", "application/octet-stream", new byte[5_242_881]);
        assertEquals("CATIMP_002", assertThrows(AppException.class, () -> service.submit(file)).getErrorCode().getCode());
        verifyNoInteractions(temp);
    }

    @Test void acceptsExpectedHeaderAndCountsRows() throws Exception {
        CategoryImportService service = new CategoryImportService(mock(RedisTemplate.class), mock(TempExcelFileManager.class),
                mock(JdbcTemplate.class), mock(PlatformTransactionManager.class), mock(IamApi.class), mock(Executor.class));
        File file = Files.createTempFile("category-import", ".xlsx").toFile();
        try {
            try (var book = new XSSFWorkbook(); var out = Files.newOutputStream(file.toPath())) {
                var sheet = book.createSheet(); var header = sheet.createRow(0);
                String[] names = {"code", "name", "parent_code", "description", "status"};
                for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
                sheet.createRow(1).createCell(0).setCellValue("CAT_1");
                sheet.createRow(2);
                book.write(out);
            }
            assertEquals(1, service.scan(file));
        } finally { Files.deleteIfExists(file.toPath()); }
    }

    @SuppressWarnings("unchecked")
    @Test void jobCannotBeReadByAnotherOwner() {
        RedisTemplate<String,Object> redis = mock(RedisTemplate.class);
        ValueOperations<String,Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        UUID id = UUID.randomUUID();
        CategoryImportJob job = new CategoryImportJob(id, UUID.randomUUID());
        when(values.get("aquasuite:category:import:" + id)).thenReturn(job);
        IamApi iam = mock(IamApi.class);
        when(iam.getCurrentUserId()).thenReturn(UUID.randomUUID());
        CategoryImportService service = new CategoryImportService(redis, mock(TempExcelFileManager.class),
                mock(JdbcTemplate.class), mock(PlatformTransactionManager.class), iam, mock(Executor.class));
        assertEquals("SYS_004", assertThrows(AppException.class, () -> service.get(id)).getErrorCode().getCode());
    }

    @Test void templateUsesFiveColumns() throws Exception {
        CategoryImportService service = new CategoryImportService(mock(RedisTemplate.class), mock(TempExcelFileManager.class),
                mock(JdbcTemplate.class), mock(PlatformTransactionManager.class), mock(IamApi.class), mock(Executor.class));
        try (var book = new XSSFWorkbook(new java.io.ByteArrayInputStream(service.template()))) {
            var row = book.getSheetAt(0).getRow(0);
            assertEquals("parent_code", row.getCell(2).getStringCellValue());
            assertEquals("status", row.getCell(4).getStringCellValue());
        }
    }

    @SuppressWarnings("unchecked")
    @Test void reportRequiresTerminalJobAndWritesUserTextAsText() throws Exception {
        RedisTemplate<String,Object> redis = mock(RedisTemplate.class);
        ValueOperations<String,Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
        IamApi iam = mock(IamApi.class);
        when(iam.getCurrentUserId()).thenReturn(owner);
        CategoryImportJob job = new CategoryImportJob(id, owner);
        when(values.get("aquasuite:category:import:" + id)).thenReturn(job);
        CategoryImportService service = new CategoryImportService(redis, mock(TempExcelFileManager.class),
                mock(JdbcTemplate.class), mock(PlatformTransactionManager.class), iam, mock(Executor.class));
        assertEquals("CATIMP_001", assertThrows(AppException.class, () -> service.report(id)).getErrorCode().getCode());
        job.status = "FAILED";
        job.rows.add(new CategoryImportJob.CategoryImportRow(2, "=1+1", "=2+2", "ERROR", "INVALID_CODE", null, null));
        try (var book = new XSSFWorkbook(new java.io.ByteArrayInputStream(service.report(id)))) {
            var row = book.getSheet("Rows").getRow(1);
            assertEquals(org.apache.poi.ss.usermodel.CellType.STRING, row.getCell(1).getCellType());
            assertEquals("=1+1", row.getCell(1).getStringCellValue());
            assertEquals(org.apache.poi.ss.usermodel.CellType.STRING, row.getCell(2).getCellType());
            assertEquals("=2+2", row.getCell(2).getStringCellValue());
        }
    }

    @Test void rejectsMoreThanTenThousandRows() throws Exception {
        CategoryImportService service = new CategoryImportService(mock(RedisTemplate.class), mock(TempExcelFileManager.class),
                mock(JdbcTemplate.class), mock(PlatformTransactionManager.class), mock(IamApi.class), mock(Executor.class));
        File file = Files.createTempFile("category-import-limit", ".xlsx").toFile();
        try {
            try (var book = new XSSFWorkbook(); var out = Files.newOutputStream(file.toPath())) {
                var sheet = book.createSheet(); var header = sheet.createRow(0);
                String[] names = {"code", "name", "parent_code", "description", "status"};
                for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
                for (int i = 1; i <= 10_001; i++) sheet.createRow(i).createCell(0).setCellValue("C" + i);
                book.write(out);
            }
            assertThrows(RuntimeException.class, () -> service.scan(file));
        } finally { Files.deleteIfExists(file.toPath()); }
    }
}
