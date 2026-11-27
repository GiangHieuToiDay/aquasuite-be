package com.aquasuite.ims.controller.catalog;

import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.dto.catalog.request.*;
import com.aquasuite.ims.dto.catalog.response.*;
import com.aquasuite.ims.entity.catalog.*;
import com.aquasuite.ims.enums.catalog.*;
import com.aquasuite.ims.excel.dto.ImportProgressDto;
import com.aquasuite.ims.excel.exporter.AbstractExcelErrorReportGenerator;
import com.aquasuite.ims.excel.storage.TempExcelFileManager;
import com.aquasuite.ims.excel.tracker.RedisImportProgressTracker;
import com.aquasuite.ims.exception.catalog.CatalogVariantErrorCode;
import com.aquasuite.ims.exception.catalog.VariantDeleteBlockedException;
import com.aquasuite.ims.repository.catalog.*;
import com.aquasuite.ims.service.catalog.BundleService;
import com.aquasuite.ims.service.catalog.ProductService;
import com.aquasuite.ims.service.catalog.impl.ProductServiceImpl;
import com.aquasuite.ims.service.catalog.excel.ProductBulkExcelImporter;
import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.dto.inventory.response.StockSummaryDto;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller Quản lý Sản phẩm, Biến thể SKU, Import Excel và Danh mục Bán hàng.
 */
@Slf4j
@RestController
public class ProductController {

    private final ProductService productService;
    private final TempExcelFileManager tempFileManager;
    private final RedisImportProgressTracker progressTracker;
    private final AbstractExcelErrorReportGenerator errorReportGenerator;
    private final ProductBulkExcelImporter productBulkExcelImporter;

    // Direct dependencies for SalesCatalogItems (legacy compatibility retained)
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductImageRepository imageRepository;
    private final InventoryService inventoryApi;
    private final BundleService bundleQueryService;

    @Autowired
    public ProductController(
            ProductService productService,
            @Autowired(required = false) TempExcelFileManager tempFileManager,
            @Autowired(required = false) RedisImportProgressTracker progressTracker,
            @Autowired(required = false) AbstractExcelErrorReportGenerator errorReportGenerator,
            @Autowired(required = false) ProductBulkExcelImporter productBulkExcelImporter,
            @Autowired(required = false) ProductVariantRepository variantRepository,
            @Autowired(required = false) ProductRepository productRepository,
            @Autowired(required = false) CategoryRepository categoryRepository,
            @Autowired(required = false) BrandRepository brandRepository,
            @Autowired(required = false) ProductImageRepository imageRepository,
            @Autowired(required = false) InventoryService inventoryApi,
            @Autowired(required = false) BundleService bundleQueryService) {
        this.productService = productService;
        this.tempFileManager = tempFileManager;
        this.progressTracker = progressTracker;
        this.errorReportGenerator = errorReportGenerator;
        this.productBulkExcelImporter = productBulkExcelImporter;
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.imageRepository = imageRepository;
        this.inventoryApi = inventoryApi;
        this.bundleQueryService = bundleQueryService;
    }

    // ── Product Shop Owner endpoints ─────────────────────────────────────────

    @PostMapping("/api/v1/catalog/products")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        ProductDetailResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Product created successfully. Please configure SKUs and variants."));
    }

    @GetMapping("/api/v1/catalog/products")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) ProductType productType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProductSummaryResponse> response = productService.listProducts(
                keyword, categoryId, brandId, status, productType, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/api/v1/catalog/products/{productId}")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductDetail(
            @PathVariable UUID productId) {
        ProductDetailResponse response = productService.getProductDetail(productId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/api/v1/catalog/products/{productId}")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductDetailResponse response = productService.updateProduct(productId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Product details updated successfully."));
    }

    @PatchMapping("/api/v1/catalog/products/{productId}/status")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProductStatus(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductStatusRequest request) {
        ProductDetailResponse response = productService.updateProductStatus(productId, request);
        String message = request.status() == ProductStatus.DISCONTINUED
                ? "Product marked as Discontinued."
                : "Cập nhật trạng thái sản phẩm thành công";
        return ResponseEntity.ok(ApiResponse.ok(response, message));
    }

    @DeleteMapping("/api/v1/catalog/products/{productId}")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Xóa sản phẩm thành công"));
    }

    // ── Variant endpoints ───────────────────────────────────────────────────

    @GetMapping("/api/v1/catalog/products/{productId}/variants")
    @PreAuthorize("hasAnyRole('SHOP_OWNER')")
    public ApiResponse<Page<VariantResponse>> listVariants(
            @PathVariable UUID productId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VariantStatus status,
            @RequestParam(defaultValue = "false") boolean missingPrice,
            @RequestParam(defaultValue = "default") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(defaultValue = "WH_MAIN") String warehouseCode,
            @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.ok(productService.list(
                productId, keyword, status, missingPrice, sortBy, sortDir, warehouseCode, pageable));
    }

    @GetMapping("/api/v1/catalog/products/{productId}/variants/stats")
    @PreAuthorize("hasAnyRole('SHOP_OWNER')")
    public ApiResponse<VariantStatsResponse> variantStats(@PathVariable UUID productId) {
        return ApiResponse.ok(productService.stats(productId));
    }

    @PostMapping("/api/v1/catalog/products/{productId}/variants")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<VariantResponse> createVariant(
            @PathVariable UUID productId, @Valid @RequestBody VariantUpsertRequest request) {
        return ApiResponse.ok(productService.create(productId, request), "SKU and variant pricing saved successfully.");
    }

    @PostMapping("/api/v1/catalog/products/{productId}/variants/matrix")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<List<VariantResponse>> matrixVariant(
            @PathVariable UUID productId, @Valid @RequestBody VariantMatrixRequest request) {
        return ApiResponse.ok(productService.createMatrix(productId, request),
                "Generated " + request.variants().size() + " variants successfully.");
    }

    @PutMapping("/api/v1/catalog/products/{productId}/variants/{variantId}")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<VariantResponse> updateVariant(
            @PathVariable UUID productId, @PathVariable UUID variantId, @Valid @RequestBody VariantUpsertRequest request) {
        return ApiResponse.ok(productService.update(productId, variantId, request));
    }

    @PatchMapping("/api/v1/catalog/products/{productId}/variants/{variantId}/status")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<VariantResponse> statusVariant(
            @PathVariable UUID productId, @PathVariable UUID variantId, @Valid @RequestBody VariantStatusRequest request) {
        return ApiResponse.ok(productService.changeStatus(productId, variantId, request));
    }

    @GetMapping("/api/v1/catalog/products/{productId}/variants/{variantId}/deletion-eligibility")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<VariantDeletionEligibilityResponse> variantEligibility(
            @PathVariable UUID productId, @PathVariable UUID variantId) {
        return ApiResponse.ok(productService.deletionEligibility(productId, variantId));
    }

    @DeleteMapping("/api/v1/catalog/products/{productId}/variants/{variantId}")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<Void>> deleteVariant(
            @PathVariable UUID productId, @PathVariable UUID variantId, @Valid @RequestBody VariantDeleteRequest request) {
        productService.delete(productId, variantId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @ExceptionHandler(VariantDeleteBlockedException.class)
    public ResponseEntity<ApiResponse<VariantDeletionEligibilityResponse>> blockedVariant(VariantDeleteBlockedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.<VariantDeletionEligibilityResponse>builder()
                .success(false).code(CatalogVariantErrorCode.DELETE_BLOCKED.getCode())
                .message(CatalogVariantErrorCode.DELETE_BLOCKED.getMessage())
                .data(exception.getEligibility()).build());
    }

    // ── Catalog Excel Import endpoints ──────────────────────────────────────

    @PostMapping(value = "/api/v1/catalog/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadAndImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "importMode", defaultValue = "SKIP_EXISTING") ImportMode importMode) {
        if (file == null || file.isEmpty()) {
            throw new com.aquasuite.ims.exception.AppException(
                    com.aquasuite.ims.exception.CommonErrorCode.INVALID_REQUEST_PARAM,
                    "File Excel import không được để trống");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.trim().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw new com.aquasuite.ims.exception.AppException(
                    com.aquasuite.ims.exception.CommonErrorCode.INVALID_REQUEST_PARAM,
                    "Chỉ hỗ trợ file Excel định dạng .xlsx");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new com.aquasuite.ims.exception.AppException(
                    com.aquasuite.ims.exception.CommonErrorCode.INVALID_REQUEST_PARAM,
                    "Dung lượng file Excel tối đa 5MB");
        }
        File tempFile = tempFileManager.saveToTempFile(file, "catalog");
        String jobId = "imp_" + UUID.randomUUID();

        log.info("Received catalog import request. JobId: {}, ImportMode: {}, FileName: {}",
                jobId, importMode, file.getOriginalFilename());

        productBulkExcelImporter.startAsyncImport(tempFile, jobId, importMode);

        Map<String, Object> result = Map.of(
                "jobId", jobId,
                "importMode", importMode.name(),
                "fileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : "catalog.xlsx");

        return ResponseEntity.accepted()
                .body(ApiResponse.ok(result, "File import đã được tiếp nhận và đang tiến hành xử lý ngầm"));
    }

    @GetMapping("/api/v1/catalog/import/{jobId}/progress")
    public ResponseEntity<ApiResponse<ImportProgressDto>> getImportProgress(
            @PathVariable("jobId") String jobId) {
        ImportProgressDto progress = progressTracker.getProgress(jobId);
        return ResponseEntity.ok(ApiResponse.ok(progress, "Tiến độ import sản phẩm"));
    }

    @GetMapping("/api/v1/catalog/import/{jobId}/error-report")
    public ResponseEntity<byte[]> downloadErrorReport(@PathVariable("jobId") String jobId) throws IOException {
        ImportProgressDto progress = progressTracker.getProgress(jobId);
        byte[] excelBytes = errorReportGenerator.generateErrorWorkbook("Catalog_Import_Errors", progress.getErrors());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Catalog_Import_Errors_" + jobId + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    // ── Sales Catalog Items (for Quotation / POS) ────────────────────────────

    @GetMapping({"/api/v1/sales/catalog-items", "/api/v1/catalog/sales-items"})
    public ResponseEntity<ApiResponse<List<SalesCatalogItemResponse>>> getSalesCatalogItems() {
        List<ProductVariant> variants = variantRepository.findAll().stream()
                .filter(v -> v.getStatus() == VariantStatus.ACTIVE)
                .toList();

        Set<UUID> productIds = variants.stream().map(ProductVariant::getProductId).collect(Collectors.toSet());
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));

        List<Category> allCategories = categoryRepository.findAll();
        Map<UUID, String> categoryNameMap = allCategories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, Category::getCategoryName));

        List<Brand> allBrands = brandRepository.findAll();
        Map<UUID, String> brandNameMap = allBrands.stream()
                .collect(Collectors.toMap(Brand::getBrandId, Brand::getBrandName));

        Set<UUID> variantIds = variants.stream().map(ProductVariant::getVariantId).collect(Collectors.toSet());
        Map<UUID, StockSummaryDto> stockMap = inventoryApi.getStockSummaries(variantIds);

        List<ProductImage> allImages = imageRepository.findAll();
        Map<UUID, String> imageMap = new HashMap<>();
        for (ProductImage img : allImages) {
            if (img.getVariantId() != null && !imageMap.containsKey(img.getVariantId())) {
                imageMap.put(img.getVariantId(), img.getImageUrl());
            } else if (img.getProductId() != null && !imageMap.containsKey(img.getProductId())) {
                imageMap.put(img.getProductId(), img.getImageUrl());
            }
        }

        List<SalesCatalogItemResponse> result = new ArrayList<>();

        for (ProductVariant v : variants) {
            Product p = productMap.get(v.getProductId());
            if (p != null && p.getProductType() == ProductType.BUNDLE) {
                continue;
            }
            boolean isCombo = false;
            String category = (p != null && p.getCategoryId() != null) ? categoryNameMap.getOrDefault(p.getCategoryId(), "Thiết bị") : "Thiết bị";
            String brand = (p != null && p.getBrandId() != null) ? brandNameMap.getOrDefault(p.getBrandId(), "TOTO") : "TOTO";
            BigDecimal stock = stockMap.containsKey(v.getVariantId()) ? stockMap.get(v.getVariantId()).availableQty() : BigDecimal.valueOf(50);
            String imgUrl = imageMap.getOrDefault(v.getVariantId(), imageMap.get(v.getProductId()));

            result.add(SalesCatalogItemResponse.builder()
                    .variantId(v.getVariantId())
                    .sku(v.getSku())
                    .name(v.getVariantName())
                    .brand(brand)
                    .category(isCombo ? "Combo Dự Án" : category)
                    .unit(v.getUnit())
                    .sellingPrice(v.getSellingPrice())
                    .availableStock(stock)
                    .isCombo(isCombo)
                    .description(p != null ? p.getDescription() : "")
                    .imageUrl(imgUrl)
                    .specifications(p != null ? p.getSpecifications() : null)
                    .build());
        }

        // Truy vấn các gói Combo (Bundle) đã phát hành từ Database
        try {
            int page = 0;
            com.aquasuite.ims.common.PageResponse<BundleResponse> bundlePage;
            do {
                bundlePage = bundleQueryService.getBundleList(new BundlePageRequest(page++, 100, null, ProductStatus.ACTIVE, null));
                if (bundlePage == null || bundlePage.getItems() == null) {
                    break;
                }
                for (BundleResponse b : bundlePage.getItems()) {
                    String bundleBrand = b.brandId() != null ? brandNameMap.getOrDefault(b.brandId(), "TOTO") : "TOTO";
                    String imgUrl = imageMap.get(b.bundleId());

                    BigDecimal minComboStock = BigDecimal.ZERO;
                    List<String> components = new ArrayList<>();
                    if (b.items() != null && !b.items().isEmpty()) {
                        BigDecimal minStock = null;
                        for (BundleResponse.Item it : b.items()) {
                            components.add(it.name() + " (" + it.sku() + ") x" + it.quantity());
                            StockSummaryDto compStock = stockMap.get(it.variantId());
                            if (compStock == null) {
                                compStock = inventoryApi.getStockSummary(it.variantId()).orElse(null);
                            }
                            BigDecimal available = (compStock != null && compStock.availableQty() != null)
                                    ? compStock.availableQty()
                                    : BigDecimal.valueOf(50);
                            BigDecimal maxCombos = available.divide(BigDecimal.valueOf(Math.max(1, it.quantity())), 0, RoundingMode.FLOOR);
                            if (minStock == null || maxCombos.compareTo(minStock) < 0) {
                                minStock = maxCombos;
                            }
                        }
                        if (minStock != null) {
                            minComboStock = minStock.max(BigDecimal.ZERO);
                        }
                    }

                    result.add(SalesCatalogItemResponse.builder()
                            .variantId(b.bundleId())
                            .sku(b.productCode())
                            .name(b.name())
                            .brand(bundleBrand)
                            .category("Combo Dự Án")
                            .unit("Gói")
                            .sellingPrice(b.packagePrice())
                            .availableStock(minComboStock)
                            .isCombo(true)
                            .description(b.description())
                            .imageUrl(imgUrl)
                            .components(components)
                            .build());
                }
            } while (page < bundlePage.getTotalPages());
        } catch (Exception ex) {
            log.warn("Could not load published bundles into sales catalog: {}", ex.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
