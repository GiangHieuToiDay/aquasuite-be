package com.aquasuite.ims.module.catalog.internal.service.excel;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.excel.storage.TempExcelFileManager;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.file.Files;
import java.util.zip.ZipFile;
import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrandParserVerificationTest {
    @Test void scanTenThousandSharedStrings() throws Exception {
        var file = Files.createTempFile("brand-verification", ".xlsx");
        try {
            try (var wb = new XSSFWorkbook(); var out = Files.newOutputStream(file)) {
                var sheet = wb.createSheet(); var header = sheet.createRow(0);
                String[] columns = {"code", "name", "description", "status"};
                for (int i=0; i<columns.length; i++) header.createCell(i).setCellValue(columns[i]);
                for (int i=0; i<10000; i++) {
                    var row = sheet.createRow(i+1);
                    row.createCell(0).setCellValue("BRAND"+i);
                    row.createCell(1).setCellValue("Repeated shared string");
                }
                wb.write(out);
            }
            var service = new BrandImportService(mock(RedisTemplate.class), mock(TempExcelFileManager.class),
                mock(JdbcTemplate.class), mock(PlatformTransactionManager.class), mock(IamApi.class), mock(Executor.class));
            long start = System.nanoTime();
            assertEquals(10000, service.scan(file.toFile()));
            try (var zip = new ZipFile(file.toFile())) {
                long expanded = zip.stream().mapToLong(entry -> entry.getSize()).sum();
                assertTrue(expanded > Files.size(file) * 5,
                    "Fixture must exercise a strongly compressed XLSX, not only ordinary rows");
                System.out.println("SCAN_10000_EXPANDED_BYTES=" + expanded);
            }
            System.out.println("SCAN_10000_BYTES=" + Files.size(file) + " ELAPSED_MS=" + (System.nanoTime()-start)/1_000_000);
        } finally { Files.deleteIfExists(file); }
    }
}
