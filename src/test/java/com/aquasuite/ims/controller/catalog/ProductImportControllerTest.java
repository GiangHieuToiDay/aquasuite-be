package com.aquasuite.ims.controller.catalog;

import com.aquasuite.ims.enums.catalog.ImportMode;
import com.aquasuite.ims.excel.dto.ExcelRowError;
import com.aquasuite.ims.excel.dto.ImportJobStatus;
import com.aquasuite.ims.excel.dto.ImportProgressDto;
import com.aquasuite.ims.excel.exception.ExcelErrorCode;
import com.aquasuite.ims.excel.exporter.AbstractExcelErrorReportGenerator;
import com.aquasuite.ims.excel.storage.TempExcelFileManager;
import com.aquasuite.ims.excel.tracker.RedisImportProgressTracker;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import com.aquasuite.ims.service.catalog.ProductService;
import com.aquasuite.ims.service.catalog.excel.ProductBulkExcelImporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller & multipart validation tests cho UC-06.10 (Import Products).
 */
@WebMvcTest(
        controllers = ProductController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.aquasuite\\.ims\\.security\\..*"))
@Import({GlobalExceptionHandler.class, ProductImportControllerTest.TestSecurityConfig.class})
class ProductImportControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProductService productService;
    @MockitoBean private TempExcelFileManager tempFileManager;
    @MockitoBean private RedisImportProgressTracker progressTracker;
    @MockitoBean private AbstractExcelErrorReportGenerator errorReportGenerator;
    @MockitoBean private ProductBulkExcelImporter productBulkExcelImporter;

    @Test
    void importEndpoints_unauthenticated_return401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "products.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/v1/catalog/import").file(file))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/catalog/import/imp_1/progress"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/catalog/import/imp_1/error-report"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(productBulkExcelImporter, progressTracker, tempFileManager);
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void importProducts_validXlsx_returns202AndTriggersAsyncImporter() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "products.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] {0x50, 0x4B, 0x03, 0x04});
        File mockTemp = new File("mock-catalog.xlsx");
        when(tempFileManager.saveToTempFile(any(), eq("catalog"))).thenReturn(mockTemp);

        mockMvc.perform(multipart("/api/v1/catalog/import")
                        .file(file)
                        .param("importMode", "OVERWRITE_EXISTING"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.importMode").value("OVERWRITE_EXISTING"))
                .andExpect(jsonPath("$.data.fileName").value("products.xlsx"))
                .andExpect(jsonPath("$.data.jobId").isNotEmpty());

        verify(productBulkExcelImporter).startAsyncImport(eq(mockTemp), anyString(), eq(ImportMode.OVERWRITE_EXISTING));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void importProducts_emptyWrongExtOrOver5Mb_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]);
        mockMvc.perform(multipart("/api/v1/catalog/import").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));

        MockMultipartFile csvFile = new MockMultipartFile(
                "file", "products.csv", "text/csv", "a,b,c".getBytes());
        mockMvc.perform(multipart("/api/v1/catalog/import").file(csvFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));

        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile bigFile = new MockMultipartFile(
                "file", "big.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                oversized);
        mockMvc.perform(multipart("/api/v1/catalog/import").file(bigFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_002"));

        verifyNoInteractions(tempFileManager, productBulkExcelImporter);
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void getProgressAndDownloadErrorReport_returnExpectedResponses() throws Exception {
        ImportProgressDto dto = ImportProgressDto.builder()
                .jobId("imp_123")
                .status(ImportJobStatus.COMPLETED_WITH_ERRORS)
                .totalRows(10)
                .processedRows(10)
                .successRows(9)
                .errorRows(1)
                .percentage(100)
                .errors(List.of(new ExcelRowError(3, "SKU-1", "Invalid")))
                .build();
        when(progressTracker.getProgress("imp_123")).thenReturn(dto);
        when(errorReportGenerator.generateErrorWorkbook(eq("Catalog_Import_Errors"), anyList()))
                .thenReturn(new byte[] {1, 2, 3, 4});

        mockMvc.perform(get("/api/v1/catalog/import/imp_123/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.data.errorRows").value(1));

        mockMvc.perform(get("/api/v1/catalog/import/imp_123/error-report"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Catalog_Import_Errors_imp_123.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(new byte[] {1, 2, 3, 4}));

        when(progressTracker.getProgress("missing")).thenThrow(new AppException(ExcelErrorCode.JOB_NOT_FOUND, "missing"));
        mockMvc.perform(get("/api/v1/catalog/import/missing/progress"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXL_004"));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(httpBasic -> { })
                    .build();
        }
    }
}
