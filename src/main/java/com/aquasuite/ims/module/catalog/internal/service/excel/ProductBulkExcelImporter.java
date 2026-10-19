package com.aquasuite.ims.module.catalog.internal.service.excel;

import com.aquasuite.ims.module.catalog.events.VariantsBulkImportedEvent;
import com.aquasuite.ims.module.catalog.events.VariantsBulkImportedEvent.InitialStockItem;
import com.aquasuite.ims.module.catalog.internal.entity.Product;
import com.aquasuite.ims.module.catalog.internal.entity.ProductImage;
import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.entity.enums.VariantStatus;
import com.aquasuite.ims.module.catalog.internal.mapper.ProductExcelMapper;
import com.aquasuite.ims.module.catalog.internal.payload.excel.ImportMode;
import com.aquasuite.ims.module.catalog.internal.payload.excel.ProductExcelRowDto;
import com.aquasuite.ims.module.catalog.internal.payload.excel.ProductImportLookupContext;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.share.excel.dto.ExcelRowError;
import com.aquasuite.ims.share.excel.importer.AbstractTwoPassExcelImporter;
import com.aquasuite.ims.share.excel.storage.TempExcelFileManager;
import com.aquasuite.ims.share.excel.tracker.RedisImportProgressTracker;

import org.dhatim.fastexcel.reader.Row;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProductBulkExcelImporter
        extends AbstractTwoPassExcelImporter<ProductExcelRowDto, ProductImportLookupContext> {

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductExcelMapper productExcelMapper;
    private final ApplicationEventPublisher eventPublisher;

    private static final Pattern PLAIN_NUMBER =
            Pattern.compile("^-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$");
    private static final Set<String> TRUE_VALUES = Set.of("YES", "Y", "TRUE", "1", "CÓ", "CO");

    private static final ThreadLocal<ImportMode> CURRENT_IMPORT_MODE = new ThreadLocal<>();

    public ProductBulkExcelImporter(
            RedisImportProgressTracker progressTracker,
            TempExcelFileManager tempFileManager,
            PlatformTransactionManager transactionManager,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            ProductImageRepository productImageRepository,
            ProductExcelMapper productExcelMapper,
            ApplicationEventPublisher eventPublisher) {
        super(progressTracker, tempFileManager, transactionManager);
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.productImageRepository = productImageRepository;
        this.productExcelMapper = productExcelMapper;
        this.eventPublisher = eventPublisher;
    }

    /** Chạy bất đồng bộ ở background thread để trả về jobId ngay cho UI */
    @Async("taskExecutor")
    public void startAsyncImport(File tempFile, String jobId, ImportMode importMode) {
        try {
            CURRENT_IMPORT_MODE.set(importMode != null ? importMode : ImportMode.SKIP_EXISTING);
            super.executeImport(tempFile, jobId);
        } finally {
            CURRENT_IMPORT_MODE.remove();
        }
    }

    // =========================================================================
    // 1. CẤU HÌNH VỊ TRÍ DÒNG & TIÊU ĐỀ CHUẨN (DÙNG CHO PASS 1)
    // =========================================================================

    @Override
    protected int getHeaderRowNumber() {
        return 2; // Dòng 1 là Nhóm gộp, Dòng 2 là Header cột
    }

    @Override
    protected int getDataStartRowNumber() {
        return 3; // Dữ liệu bắt đầu từ Dòng 3
    }

    @Override
    protected List<String> getExpectedHeaders() {
        return List.of(
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
                "Định mức tồn tối thiểu (minimum_qty)");
    }

    // =========================================================================
    // 2. CHUẨN BỊ TỪ ĐIỂN CATEGORY & BRAND TRÊN RAM (CHẠY 1 LẦN TRƯỚC PASS 2)
    // =========================================================================

    @Override
    protected ProductImportLookupContext prepareLookupContext() {
        Map<String, UUID> categoryMap =
                categoryRepository.findAllActive().stream()
                        .collect(
                                Collectors.toMap(
                                        c -> normalizeCode(c.getCategoryCode()),
                                        c -> c.getCategoryId(),
                                        (existing, replacement) -> existing));

        Map<String, UUID> brandMap =
                brandRepository.findAllActive().stream()
                        .collect(
                                Collectors.toMap(
                                        b -> normalizeCode(b.getBrandCode()),
                                        b -> b.getBrandId(),
                                        (existing, replacement) -> existing));

        ImportMode mode =
                CURRENT_IMPORT_MODE.get() != null
                        ? CURRENT_IMPORT_MODE.get()
                        : ImportMode.SKIP_EXISTING;

        return new ProductImportLookupContext(mode, categoryMap, brandMap);
    }

    // =========================================================================
    // 3. ĐỌC & VALIDATE CÚ PHÁP TỪNG DÒNG EXCEL (BẮT BUỘC ĐỦ 4 CỘT ĐỊNH DANH)
    // =========================================================================

    @Override
    protected ProductExcelRowDto mapRowToDto(Row row, int rowNumber) {
        String productCode = normalizeCode(getRequiredCell(row, 0, "Mã nhóm SP (product_code)"));
        String productName = getRequiredCell(row, 1, "Tên sản phẩm gốc (product_name)");
        String categoryCode = normalizeCode(getRequiredCell(row, 2, "Mã danh mục (category_code)"));
        String brandCode = normalizeCode(getOptionalCell(row, 3));
        String sku = normalizeCode(getRequiredCell(row, 4, "Mã SKU (sku)"));
        String variantName = getRequiredCell(row, 5, "Tên biến thể (variant_name)");
        String unit = getRequiredCell(row, 6, "Đơn vị tính (unit)");

        BigDecimal costPrice = parseBigDecimal(row, 9, "Giá vốn (cost_price)", null);
        BigDecimal sellingPrice = parseBigDecimal(row, 10, "Giá bán (selling_price)", null);
        if (costPrice.compareTo(BigDecimal.ZERO) < 0
                || sellingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Giá vốn phải >= 0 và Giá bán phải > 0");
        }

        BigDecimal initialStock = parseBigDecimal(row, 15, "Tồn kho đầu kỳ", BigDecimal.ZERO);
        BigDecimal minimumQty = parseBigDecimal(row, 16, "Định mức tồn tối thiểu", BigDecimal.ZERO);
        if (initialStock.compareTo(BigDecimal.ZERO) < 0
                || minimumQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Tồn kho đầu kỳ và Định mức tồn tối thiểu không được âm");
        }

        return ProductExcelRowDto.builder()
                .rowNumber(rowNumber)
                .productCode(productCode)
                .productName(productName)
                .categoryCode(categoryCode)
                .brandCode(brandCode)
                .sku(sku)
                .variantName(variantName)
                .unit(unit)
                .color(getOptionalCell(row, 7))
                .size(getOptionalCell(row, 8))
                .costPrice(costPrice)
                .sellingPrice(sellingPrice)
                .serialTracking(parseBoolean(getOptionalCell(row, 11)))
                .technicalSpec(parseTechnicalSpecToMap(getOptionalCell(row, 12)))
                .imageUrls(parseImageUrls(getOptionalCell(row, 13)))
                .description(getOptionalCell(row, 14))
                .initialStock(initialStock)
                .minimumQty(minimumQty)
                .build();
    }

    // =========================================================================
    // 4. LƯU BATCH XUỐNG DB
    // =========================================================================

    @Override
    protected int processAndPersistBatch(
            List<ProductExcelRowDto> batch,
            ProductImportLookupContext context,
            List<ExcelRowError> batchErrors) {

        // 4.1. Lọc các dòng hợp lệ về mặt Nghiệp vụ
        List<ProductExcelRowDto> validRowsInBatch = new ArrayList<>(batch.size());
        for (ProductExcelRowDto row : batch) {
            if (!context.getCategoryCodeToIdMap().containsKey(row.categoryCode())) {
                batchErrors.add(
                        new ExcelRowError(
                                row.rowNumber(),
                                row.sku(),
                                "Mã danh mục '"
                                        + row.categoryCode()
                                        + "' không tồn tại trong hệ thống"));
                continue;
            }
            if (row.brandCode() != null
                    && !context.getBrandCodeToIdMap().containsKey(row.brandCode())) {
                batchErrors.add(
                        new ExcelRowError(
                                row.rowNumber(),
                                row.sku(),
                                "Mã thương hiệu '"
                                        + row.brandCode()
                                        + "' không tồn tại trong hệ thống"));
                continue;
            }
            String signature =
                    row.productName()
                            + "|"
                            + row.categoryCode()
                            + "|"
                            + (row.brandCode() != null ? row.brandCode() : "");
            String previousSignature =
                    context.getProductSignatureInFile().putIfAbsent(row.productCode(), signature);
            if (previousSignature != null && !previousSignature.equals(signature)) {
                batchErrors.add(
                        new ExcelRowError(
                                row.rowNumber(),
                                row.sku(),
                                "Mã nhóm SP '"
                                        + row.productCode()
                                        + "' đã xuất hiện ở dòng trước với Tên/Danh mục/Thương hiệu khác"));
                continue;
            }
            if (!context.getSeenSkusInFile().add(row.sku())) {
                batchErrors.add(
                        new ExcelRowError(
                                row.rowNumber(),
                                row.sku(),
                                "Mã SKU '"
                                        + row.sku()
                                        + "' bị lặp lại nhiều lần trong cùng file Excel"));
                continue;
            }
            validRowsInBatch.add(row);
        }

        if (validRowsInBatch.isEmpty()) return 0;

        // 4.2. Query các Product và Variant đã có sẵn trong DB của mẻ này
        Set<String> productCodes =
                validRowsInBatch.stream()
                        .map(ProductExcelRowDto::productCode)
                        .collect(Collectors.toSet());
        Map<String, Product> productMap =
                productRepository.findByProductCodeIn(productCodes).stream()
                        .collect(Collectors.toMap(Product::getProductCode, Function.identity()));

        Set<UUID> existingVariantParents = new HashSet<>();
        variantRepository
                .findBySkuIn(validRowsInBatch.stream().map(ProductExcelRowDto::sku).toList())
                .forEach(v -> existingVariantParents.add(v.getProductId()));
        Set<UUID> bundleParentIds =
                productRepository.findAllById(existingVariantParents).stream()
                        .filter(
                                p ->
                                        p.getProductType()
                                                == com.aquasuite.ims.module.catalog.internal.entity
                                                        .enums.ProductType.BUNDLE)
                        .map(Product::getProductId)
                        .collect(Collectors.toSet());

        Set<String> skus =
                validRowsInBatch.stream().map(ProductExcelRowDto::sku).collect(Collectors.toSet());
        Map<String, ProductVariant> variantMap =
                variantRepository.findBySkuIn(skus).stream()
                        .collect(Collectors.toMap(ProductVariant::getSku, Function.identity()));

        // 4.3. Duyệt các dòng hợp lệ -> Xử lý theo ImportMode
        int successCount = 0;
        ImportMode mode = context.getImportMode();

        for (ProductExcelRowDto row : validRowsInBatch) {
            ProductVariant existingVariant = variantMap.get(row.sku());
            Product existingProduct = productMap.get(row.productCode());

            if ((existingProduct != null
                            && existingProduct.getProductType()
                                    == com.aquasuite.ims.module.catalog.internal.entity.enums
                                            .ProductType.BUNDLE)
                    || (existingVariant != null
                            && bundleParentIds.contains(existingVariant.getProductId()))) {
                batchErrors.add(
                        new ExcelRowError(
                                row.rowNumber(),
                                row.sku(),
                                "Combo phải được quản lý qua API combo, không được sửa bằng import"));
                continue;
            }
            if (existingProduct != null && existingProduct.getStatus() == ProductStatus.DELETED) {
                batchErrors.add(
                        new ExcelRowError(
                                row.rowNumber(),
                                row.sku(),
                                "Mã nhóm SP '"
                                        + row.productCode()
                                        + "' đã bị xóa trên hệ thống, hãy khôi phục hoặc dùng mã khác"));
                continue;
            }
            if (existingVariant != null && existingVariant.getStatus() == VariantStatus.DELETED) {
                batchErrors.add(
                        new ExcelRowError(
                                row.rowNumber(),
                                row.sku(),
                                "Mã SKU '"
                                        + row.sku()
                                        + "' đã bị xóa trên hệ thống, hãy khôi phục hoặc dùng mã khác"));
                continue;
            }

            if (existingVariant != null && mode == ImportMode.SKIP_EXISTING) {
                batchErrors.add(
                        new ExcelRowError(
                                row.rowNumber(),
                                row.sku(),
                                "Mã SKU '"
                                        + row.sku()
                                        + "' đã tồn tại trên hệ thống (Bỏ qua do chọn chế độ Tạo mới)"));
                continue;
            }

            UUID categoryId = context.getCategoryCodeToIdMap().get(row.categoryCode());
            UUID brandId =
                    row.brandCode() != null
                            ? context.getBrandCodeToIdMap().get(row.brandCode())
                            : null;

            if (existingVariant != null
                    && existingProduct != null
                    && !existingVariant.getProductId().equals(existingProduct.getProductId())) {
                batchErrors.add(
                        new ExcelRowError(
                                row.rowNumber(),
                                row.sku(),
                                "Mã SKU '"
                                        + row.sku()
                                        + "' đang thuộc nhóm SP khác trên hệ thống, không thể chuyển nhóm bằng Import"));
                continue;
            }

            // A. Xử lý bảng cha products
            Product product = existingProduct;
            if (product == null) {
                product = productExcelMapper.toNewProduct(row, categoryId, brandId);
                entityManager.persist(product);
                productMap.put(row.productCode(), product);
            } else if (mode == ImportMode.OVERWRITE_EXISTING) {
                productExcelMapper.updateProduct(product, row, categoryId, brandId);
            }

            // B. Xử lý bảng con product_variants & product_images
            if (existingVariant == null) {
                ProductVariant newVariant =
                        productExcelMapper.toNewVariant(row, product.getProductId());
                entityManager.persist(newVariant);
                variantMap.put(row.sku(), newVariant);

                persistVariantImages(
                        product.getProductId(), newVariant.getVariantId(), row.imageUrls());

                context.getInitialStockItemsToPublish()
                        .add(
                                new InitialStockItem(
                                        newVariant.getVariantId(),
                                        row.sku(),
                                        row.initialStock(),
                                        row.minimumQty()));
            } else {
                productExcelMapper.updateVariant(existingVariant, row);

                if (row.imageUrls() != null && !row.imageUrls().isEmpty()) {
                    productImageRepository.deleteByVariantId(existingVariant.getVariantId());
                    persistVariantImages(
                            product.getProductId(),
                            existingVariant.getVariantId(),
                            row.imageUrls());
                }
            }

            successCount++;
        }

        return successCount;
    }

    // =========================================================================
    // 5. SAU KHI HOÀN TẤT IMPORT -> BẮN EVENT SANG INVENTORY
    // =========================================================================

    @Override
    protected void onImportCompleted(
            String jobId, int totalSuccess, int totalErrors, ProductImportLookupContext context) {
        if (!context.getInitialStockItemsToPublish().isEmpty()) {
            eventPublisher.publishEvent(
                    new VariantsBulkImportedEvent(jobId, context.getInitialStockItemsToPublish()));
        }
    }

    // =========================================================================
    // 6. CÁC HÀM HELPER PARSE CELL & LƯU HÌNH ẢNH
    // =========================================================================

    private void persistVariantImages(UUID productId, UUID variantId, List<String> imageUrls) {
        List<ProductImage> images =
                productExcelMapper.toVariantImages(productId, variantId, imageUrls);
        for (ProductImage img : images) {
            entityManager.persist(img);
        }
    }

    private String getRequiredCell(Row row, int colIdx, String fieldName) {
        String val = getOptionalCell(row, colIdx);
        if (val == null) {
            throw new IllegalArgumentException(
                    "Cột bắt buộc [" + fieldName + "] không được để trống");
        }
        return val;
    }

    private String getOptionalCell(Row row, int colIdx) {
        if (row == null || colIdx >= row.getCellCount()) return null;
        String text = row.getCellText(colIdx);
        return (text == null || text.isBlank()) ? null : text.trim();
    }

    private BigDecimal parseBigDecimal(
            Row row, int colIdx, String fieldName, BigDecimal defaultValue) {
        String val = getOptionalCell(row, colIdx);
        if (val == null) {
            if (defaultValue != null) return defaultValue;
            throw new IllegalArgumentException("Cột số [" + fieldName + "] không được để trống");
        }
        String compact = val.replace("\u00A0", "").replace(" ", "");
        if (!PLAIN_NUMBER.matcher(compact).matches()) {
            throw new IllegalArgumentException(
                    "Giá trị '"
                            + val
                            + "' ở cột ["
                            + fieldName
                            + "] không hợp lệ. Chỉ nhập số thuần, không dùng dấu phân cách hàng nghìn (VD: 1500000 hoặc 12.5)");
        }
        return new BigDecimal(compact);
    }

    private String normalizeCode(String raw) {
        return raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
    }

    private boolean parseBoolean(String raw) {
        return raw != null && TRUE_VALUES.contains(raw.trim().toUpperCase(Locale.ROOT));
    }

    private Map<String, Object> parseTechnicalSpecToMap(String rawSpec) {
        if (rawSpec == null || rawSpec.isBlank()) return Collections.emptyMap();
        Map<String, Object> specMap = new LinkedHashMap<>();
        for (String pair : rawSpec.split("[;\n]")) {
            if (pair.isBlank()) continue;
            int colonIdx = pair.indexOf(':');
            if (colonIdx > 0 && colonIdx < pair.length() - 1) {
                specMap.put(
                        pair.substring(0, colonIdx).trim(), pair.substring(colonIdx + 1).trim());
            } else {
                specMap.put("spec_" + (specMap.size() + 1), pair.trim());
            }
        }
        return specMap;
    }

    private List<String> parseImageUrls(String rawUrls) {
        if (rawUrls == null || rawUrls.isBlank()) return Collections.emptyList();
        return Arrays.stream(rawUrls.split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
