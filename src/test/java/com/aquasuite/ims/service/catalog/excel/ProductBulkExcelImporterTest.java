package com.aquasuite.ims.service.catalog.excel;

import com.aquasuite.ims.entity.catalog.Brand;
import com.aquasuite.ims.entity.catalog.Category;
import com.aquasuite.ims.entity.catalog.Product;
import com.aquasuite.ims.entity.catalog.ProductImage;
import com.aquasuite.ims.entity.catalog.ProductVariant;
import com.aquasuite.ims.enums.catalog.BrandStatus;
import com.aquasuite.ims.enums.catalog.CategoryStatus;
import com.aquasuite.ims.enums.catalog.ImportMode;
import com.aquasuite.ims.enums.catalog.ProductStatus;
import com.aquasuite.ims.enums.catalog.ProductType;
import com.aquasuite.ims.enums.catalog.VariantStatus;
import com.aquasuite.ims.event.catalog.VariantsBulkImportedEvent;
import com.aquasuite.ims.excel.dto.ExcelRowError;
import com.aquasuite.ims.excel.dto.ImportJobStatus;
import com.aquasuite.ims.excel.dto.ImportProgressDto;
import com.aquasuite.ims.excel.exception.ExcelErrorCode;
import com.aquasuite.ims.excel.storage.TempExcelFileManager;
import com.aquasuite.ims.excel.tracker.RedisImportProgressTracker;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.mapper.catalog.ProductExcelMapper;
import com.aquasuite.ims.repository.catalog.BrandRepository;
import com.aquasuite.ims.repository.catalog.CategoryRepository;
import com.aquasuite.ims.repository.catalog.ProductImageRepository;
import com.aquasuite.ims.repository.catalog.ProductRepository;
import com.aquasuite.ims.repository.catalog.ProductVariantRepository;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho ProductBulkExcelImporter (UC-06.10 Import Products from Excel).
 */
@ExtendWith(MockitoExtension.class)
class ProductBulkExcelImporterTest {

    private static final List<String> EXPECTED_HEADERS = List.of(
            "Mã nhóm SP (product_code) *",
            "Tên sản phẩm gốc (product_name) *",
            "Mã danh mục (category_code) *",
            "Mã thương hiệu (brand_code)",
            "Mã SKU (sku) *",
            "Tên biến thể (variant_name) *",
            "Đơn vị tính (unit) *",
            "Màu sắc (color)",
            "Kích thước (size)",
            "Giá vốn (cost_price) *",
            "Giá bán (selling_price) *",
            "Quản lý Serial (serial_tracking)",
            "Thông số kỹ thuật (technical_spec)",
            "Link hình ảnh (image_urls)",
            "Mô tả (description)",
            "Tồn kho đầu kỳ (initial_stock)",
            "Định mức tồn tối thiểu (minimum_qty)"
    );

    @Mock private RedisImportProgressTracker progressTracker;
    @Mock private TempExcelFileManager tempFileManager;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EntityManager entityManager;

    private ProductBulkExcelImporter importer;
    private Map<String, ImportProgressDto> jobs;
    private UUID categoryId;
    private UUID brandId;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager txManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {}

            @Override
            public void rollback(TransactionStatus status) {}
        };

        importer = new ProductBulkExcelImporter(
                progressTracker,
                tempFileManager,
                txManager,
                categoryRepository,
                brandRepository,
                productRepository,
                variantRepository,
                productImageRepository,
                new ProductExcelMapper(),
                eventPublisher
        );
        ReflectionTestUtils.setField(importer, "entityManager", entityManager);

        jobs = new ConcurrentHashMap<>();
        categoryId = UUID.randomUUID();
        brandId = UUID.randomUUID();

        lenient().doAnswer(inv -> {
            String jobId = inv.getArgument(0);
            int total = inv.getArgument(1);
            jobs.put(jobId, ImportProgressDto.builder()
                    .jobId(jobId)
                    .status(ImportJobStatus.PROCESSING)
                    .totalRows(total)
                    .build());
            return null;
        }).when(progressTracker).initJob(anyString(), anyInt());

        lenient().doAnswer(inv -> {
            String jobId = inv.getArgument(0);
            int processed = inv.getArgument(1);
            int success = inv.getArgument(2);
            List<ExcelRowError> errs = inv.getArgument(3);
            ImportProgressDto dto = jobs.get(jobId);
            dto.setProcessedRows(dto.getProcessedRows() + processed);
            dto.setSuccessRows(dto.getSuccessRows() + success);
            dto.setErrorRows(dto.getErrorRows() + errs.size());
            dto.getErrors().addAll(errs);
            return null;
        }).when(progressTracker).updateBatchProgress(anyString(), anyInt(), anyInt(), anyList());

        lenient().when(progressTracker.getProgress(anyString()))
                .thenAnswer(inv -> jobs.get(inv.<String>getArgument(0)));

        lenient().doAnswer(inv -> {
            Object entity = inv.getArgument(0);
            if (entity instanceof Product p && p.getProductId() == null) {
                p.setProductId(UUID.randomUUID());
            } else if (entity instanceof ProductVariant v && v.getVariantId() == null) {
                v.setVariantId(UUID.randomUUID());
            }
            return null;
        }).when(entityManager).persist(any());

        lenient().when(categoryRepository.findAllActive()).thenReturn(List.of(
                Category.builder().categoryId(categoryId).categoryCode("SANITARY").categoryName("Thiết bị vệ sinh").status(CategoryStatus.ACTIVE).build()
        ));
        lenient().when(brandRepository.findAllActive()).thenReturn(List.of(
                Brand.builder().brandId(brandId).brandCode("TOTO").brandName("TOTO").status(BrandStatus.ACTIVE).build()
        ));
        lenient().when(productRepository.findByProductCodeIn(any())).thenReturn(List.of());
        lenient().when(variantRepository.findBySkuIn(any())).thenReturn(List.of());
        lenient().when(productRepository.findAllById(any())).thenReturn(List.of());
    }

    private File writeWorkbook(String fileName, List<String> headers, List<List<String>> rows) throws Exception {
        File file = tempDir.resolve(fileName).toFile();
        try (XSSFWorkbook wb = new XSSFWorkbook(); FileOutputStream out = new FileOutputStream(file)) {
            Sheet sheet = wb.createSheet("Import_Data");
            Row groupRow = sheet.createRow(0);
            groupRow.createCell(0).setCellValue("Thông tin sản phẩm");
            Row headerRow = sheet.createRow(1);
            for (int c = 0; c < headers.size(); c++) {
                headerRow.createCell(c).setCellValue(headers.get(c));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row dataRow = sheet.createRow(2 + r);
                List<String> cells = rows.get(r);
                for (int c = 0; c < cells.size(); c++) {
                    if (cells.get(c) != null) {
                        dataRow.createCell(c).setCellValue(cells.get(c));
                    }
                }
            }
            wb.write(out);
        }
        return file;
    }

    private List<String> validRow(String productCode, String sku) {
        return List.of(
                productCode,
                "Bồn cầu thông minh",
                "sanitary",
                "toto",
                sku,
                "Trắng sứ 1 khối",
                "Cái",
                "Trắng",
                "700x400",
                "5000000",
                "7500000",
                "YES",
                "Áp lực: 0.5 MPa; Xuất xứ Nhật",
                "https://cdn.aqua/img1.jpg, https://cdn.aqua/img2.jpg",
                "Sản phẩm cao cấp",
                "12",
                "3"
        );
    }

    @Test
    @DisplayName("Pass 1: Sai tiêu đề cột -> ném INVALID_HEADER_SCHEMA, đánh dấu thất bại và dọn file tạm")
    void executeImport_InvalidHeaderSchema_ThrowsAndCleansUp() throws Exception {
        List<String> badHeaders = new ArrayList<>(EXPECTED_HEADERS);
        badHeaders.set(4, "Sai Ten Cot");
        File file = writeWorkbook("bad_header.xlsx", badHeaders, List.of(validRow("SP-01", "SKU-01")));

        assertThatThrownBy(() -> importer.startAsyncImport(file, "job-bad-header", ImportMode.SKIP_EXISTING))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ExcelErrorCode.INVALID_HEADER_SCHEMA);

        verify(progressTracker).markFailed(eq("job-bad-header"), anyString());
        verify(tempFileManager).deleteQuietly(file);
        verify(entityManager, never()).persist(any());
    }

    @Test
    @DisplayName("Pass 1: File chỉ có header không có dòng dữ liệu -> ném NO_DATA_ROWS")
    void executeImport_NoDataRows_ThrowsNoDataRows() throws Exception {
        File file = writeWorkbook("empty_data.xlsx", EXPECTED_HEADERS, List.of());

        assertThatThrownBy(() -> importer.startAsyncImport(file, "job-no-rows", ImportMode.SKIP_EXISTING))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ExcelErrorCode.NO_DATA_ROWS);

        verify(progressTracker).markFailed(eq("job-no-rows"), anyString());
        verify(tempFileManager).deleteQuietly(file);
    }

    @Test
    @DisplayName("Pass 1: File hỏng / không đọc được -> ném FILE_READ_ERROR")
    void executeImport_CorruptFile_ThrowsFileReadError() throws Exception {
        Path corruptPath = tempDir.resolve("corrupt.xlsx");
        Files.writeString(corruptPath, "not-an-xlsx-zip-payload");
        File corruptFile = corruptPath.toFile();

        assertThatThrownBy(() -> importer.startAsyncImport(corruptFile, "job-corrupt", ImportMode.SKIP_EXISTING))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ExcelErrorCode.FILE_READ_ERROR);

        verify(progressTracker).markFailed(eq("job-corrupt"), anyString());
        verify(tempFileManager).deleteQuietly(corruptFile);
    }

    @Test
    @DisplayName("Pass 2 Happy Path: Tạo SPU mới, SKU mới, 2 ảnh, parse spec và bắn VariantsBulkImportedEvent")
    void executeImport_SkipExisting_CreatesNewProductVariantImagesAndEvent() throws Exception {
        File file = writeWorkbook("valid.xlsx", EXPECTED_HEADERS, List.of(validRow("sp-001", "sku-001")));

        importer.startAsyncImport(file, "job-happy", ImportMode.SKIP_EXISTING);

        ArgumentCaptor<Object> persistedCaptor = ArgumentCaptor.forClass(Object.class);
        verify(entityManager, times(4)).persist(persistedCaptor.capture());

        List<Object> persisted = persistedCaptor.getAllValues();
        assertThat(persisted).filteredOn(Product.class::isInstance).hasSize(1);
        ProductVariant savedVariant = persisted.stream()
                .filter(ProductVariant.class::isInstance)
                .map(ProductVariant.class::cast)
                .findFirst().orElseThrow();
        assertThat(savedVariant.getSku()).isEqualTo("SKU-001");
        assertThat(savedVariant.getSerialTracking()).isTrue();
        assertThat(savedVariant.getTechnicalSpec())
                .containsEntry("Áp lực", "0.5 MPa")
                .containsEntry("spec_2", "Xuất xứ Nhật");
        assertThat(persisted).filteredOn(ProductImage.class::isInstance).hasSize(2);

        ArgumentCaptor<VariantsBulkImportedEvent> eventCaptor = ArgumentCaptor.forClass(VariantsBulkImportedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().jobId()).isEqualTo("job-happy");
        assertThat(eventCaptor.getValue().items()).hasSize(1);
        assertThat(eventCaptor.getValue().items().get(0).initialStock()).isEqualByComparingTo("12");
        assertThat(eventCaptor.getValue().items().get(0).minimumQty()).isEqualByComparingTo("3");

        verify(progressTracker).markCompleted("job-happy", "Import completed successfully: 1 created/updated, 0 errors.");
        verify(tempFileManager).deleteQuietly(file);
    }

    @Test
    @DisplayName("Pass 2 Validation: Ghi nhận lỗi cú pháp ô, mã danh mục/thương hiệu sai, trùng SPU signature và trùng SKU")
    void executeImport_SyntaxAndBusinessErrors_RecordedPerRowAndValidRowStillSaved() throws Exception {
        List<String> badNumber = new ArrayList<>(validRow("SP-ERR1", "SKU-ERR1"));
        badNumber.set(9, "1,500,000");

        List<String> badCategory = new ArrayList<>(validRow("SP-ERR2", "SKU-ERR2"));
        badCategory.set(2, "UNKNOWN_CAT");

        List<String> badBrand = new ArrayList<>(validRow("SP-ERR3", "SKU-ERR3"));
        badBrand.set(3, "UNKNOWN_BRAND");

        List<String> validFirst = validRow("SP-GOOD", "SKU-GOOD");

        List<String> conflictingSpu = new ArrayList<>(validRow("SP-GOOD", "SKU-CONFLICT"));
        conflictingSpu.set(1, "Ten San Pham Khac");

        List<String> duplicateSku = validRow("SP-GOOD", "SKU-GOOD");

        File file = writeWorkbook("mixed_errors.xlsx", EXPECTED_HEADERS,
                List.of(badNumber, badCategory, badBrand, validFirst, conflictingSpu, duplicateSku));

        importer.startAsyncImport(file, "job-errors", ImportMode.SKIP_EXISTING);

        ImportProgressDto progress = jobs.get("job-errors");
        assertThat(progress.getProcessedRows()).isEqualTo(6);
        assertThat(progress.getSuccessRows()).isEqualTo(1);
        assertThat(progress.getErrorRows()).isEqualTo(5);
        assertThat(progress.getErrors())
                .extracting(ExcelRowError::errorMessage)
                .anySatisfy(msg -> assertThat(msg).contains("không dùng dấu phân cách hàng nghìn"))
                .anySatisfy(msg -> assertThat(msg).contains("Mã danh mục"))
                .anySatisfy(msg -> assertThat(msg).contains("Mã thương hiệu"))
                .anySatisfy(msg -> assertThat(msg).contains("Tên/Danh mục/Thương hiệu khác"))
                .anySatisfy(msg -> assertThat(msg).contains("bị lặp lại nhiều lần"));
    }

    @Test
    @DisplayName("Pass 2 Guard: Chặn BUNDLE product, Product/Variant DELETED, và SKU chuyển nhóm SP khác")
    void executeImport_ProtectedTargets_BlockedWithErrors() throws Exception {
        UUID bundleProductId = UUID.randomUUID();
        UUID deletedProductId = UUID.randomUUID();
        UUID otherProductId = UUID.randomUUID();

        Product bundleProd = Product.builder()
                .productId(bundleProductId).productCode("SP-BUNDLE")
                .productType(ProductType.BUNDLE).status(ProductStatus.ACTIVE).build();
        Product deletedProd = Product.builder()
                .productId(deletedProductId).productCode("SP-DEL")
                .productType(ProductType.NORMAL).status(ProductStatus.DELETED).build();
        Product activeProd = Product.builder()
                .productId(UUID.randomUUID()).productCode("SP-ACTIVE")
                .productType(ProductType.NORMAL).status(ProductStatus.ACTIVE).build();

        ProductVariant deletedVar = ProductVariant.builder()
                .variantId(UUID.randomUUID()).productId(activeProd.getProductId())
                .sku("SKU-DEL").status(VariantStatus.DELETED).build();
        ProductVariant crossGroupVar = ProductVariant.builder()
                .variantId(UUID.randomUUID()).productId(otherProductId)
                .sku("SKU-CROSS").status(VariantStatus.ACTIVE).build();

        when(productRepository.findByProductCodeIn(any())).thenReturn(List.of(bundleProd, deletedProd, activeProd));
        when(variantRepository.findBySkuIn(any())).thenReturn(List.of(deletedVar, crossGroupVar));
        when(productRepository.findAllById(any())).thenReturn(List.of(activeProd));

        File file = writeWorkbook("guards.xlsx", EXPECTED_HEADERS, List.of(
                validRow("SP-BUNDLE", "SKU-B1"),
                validRow("SP-DEL", "SKU-D1"),
                validRow("SP-ACTIVE", "SKU-DEL"),
                validRow("SP-ACTIVE", "SKU-CROSS")
        ));

        importer.startAsyncImport(file, "job-guards", ImportMode.OVERWRITE_EXISTING);

        ImportProgressDto progress = jobs.get("job-guards");
        assertThat(progress.getSuccessRows()).isZero();
        assertThat(progress.getErrorRows()).isEqualTo(4);
        assertThat(progress.getErrors())
                .extracting(ExcelRowError::errorMessage)
                .anySatisfy(msg -> assertThat(msg).contains("bundle API"))
                .anySatisfy(msg -> assertThat(msg).contains("SP-DEL").contains("đã bị xóa"))
                .anySatisfy(msg -> assertThat(msg).contains("SKU-DEL").contains("đã bị xóa"))
                .anySatisfy(msg -> assertThat(msg).contains("thuộc nhóm SP khác"));
    }

    @Test
    @DisplayName("Pass 2 Modes: SKIP_EXISTING bỏ qua SKU đã có; OVERWRITE_EXISTING cập nhật và thay ảnh")
    void executeImport_SkipExistingVsOverwriteExisting() throws Exception {
        UUID existingProductId = UUID.randomUUID();
        UUID existingVariantId = UUID.randomUUID();
        Product existingProduct = Product.builder()
                .productId(existingProductId).productCode("SP-EXIST")
                .categoryId(categoryId).productName("Tên cũ")
                .productType(ProductType.NORMAL).status(ProductStatus.ACTIVE).build();
        ProductVariant existingVariant = ProductVariant.builder()
                .variantId(existingVariantId).productId(existingProductId)
                .sku("SKU-EXIST").variantName("Biến thể cũ").unit("cái")
                .costPrice(BigDecimal.ONE).sellingPrice(BigDecimal.TEN)
                .status(VariantStatus.ACTIVE).build();

        when(productRepository.findByProductCodeIn(any())).thenReturn(List.of(existingProduct));
        when(variantRepository.findBySkuIn(any())).thenReturn(List.of(existingVariant));
        when(productRepository.findAllById(any())).thenReturn(List.of(existingProduct));

        File skipFile = writeWorkbook("skip.xlsx", EXPECTED_HEADERS, List.of(validRow("SP-EXIST", "SKU-EXIST")));
        importer.startAsyncImport(skipFile, "job-skip", ImportMode.SKIP_EXISTING);

        assertThat(jobs.get("job-skip").getSuccessRows()).isZero();
        assertThat(jobs.get("job-skip").getErrors().get(0).errorMessage()).contains("Bỏ qua do chọn chế độ Tạo mới");
        verify(productImageRepository, never()).deleteByVariantId(any());

        File overwriteFile = writeWorkbook("overwrite.xlsx", EXPECTED_HEADERS, List.of(validRow("SP-EXIST", "SKU-EXIST")));
        importer.startAsyncImport(overwriteFile, "job-overwrite", ImportMode.OVERWRITE_EXISTING);

        assertThat(jobs.get("job-overwrite").getSuccessRows()).isEqualTo(1);
        assertThat(existingProduct.getProductName()).isEqualTo("Bồn cầu thông minh");
        assertThat(existingVariant.getSellingPrice()).isEqualByComparingTo("7500000");
        verify(productImageRepository).deleteByVariantId(existingVariantId);
        verify(entityManager, times(2)).persist(any(ProductImage.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Pass 2 Batching: Chia batch 500 dòng, flush & clear từng transaction trước khi sang mẻ tiếp theo")
    void executeImport_MoreThan500Rows_FlushesAndClearsTwoBatches() throws Exception {
        List<List<String>> rows = new ArrayList<>(502);
        for (int i = 1; i <= 502; i++) {
            rows.add(validRow("SP-BATCH-" + i, "SKU-BATCH-" + i));
        }
        File file = writeWorkbook("batch_502.xlsx", EXPECTED_HEADERS, rows);

        importer.startAsyncImport(file, "job-batch", ImportMode.SKIP_EXISTING);

        ImportProgressDto progress = jobs.get("job-batch");
        assertThat(progress.getTotalRows()).isEqualTo(502);
        assertThat(progress.getProcessedRows()).isEqualTo(502);
        assertThat(progress.getSuccessRows()).isEqualTo(502);
        assertThat(progress.getErrorRows()).isZero();

        verify(entityManager, times(2)).flush();
        verify(entityManager, times(2)).clear();
        verify(progressTracker, times(2)).updateBatchProgress(eq("job-batch"), anyInt(), anyInt(), anyList());
        verify(eventPublisher, atLeastOnce()).publishEvent(any(VariantsBulkImportedEvent.class));
    }
}
