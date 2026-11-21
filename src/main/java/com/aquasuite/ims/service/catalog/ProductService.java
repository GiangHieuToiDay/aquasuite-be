package com.aquasuite.ims.service.catalog;

import com.aquasuite.ims.dto.catalog.request.*;
import com.aquasuite.ims.dto.catalog.response.*;
import com.aquasuite.ims.enums.catalog.ProductStatus;
import com.aquasuite.ims.enums.catalog.ProductType;
import com.aquasuite.ims.enums.catalog.VariantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý sản phẩm và biến thể SKU hợp nhất.
 */
public interface ProductService {

    // ── Product Operations ───────────────────────────────────────────────────

    /** Tạo mới sản phẩm cùng các biến thể và hình ảnh */
    ProductDetailResponse createProduct(CreateProductRequest request);

    /** Lấy danh sách sản phẩm có tìm kiếm, lọc và phân trang */
    Page<ProductSummaryResponse> listProducts(
            String keyword,
            UUID categoryId,
            UUID brandId,
            ProductStatus status,
            ProductType productType,
            Pageable pageable
    );

    /** Xem chi tiết sản phẩm theo ID */
    ProductDetailResponse getProductDetail(UUID productId);

    /** Cập nhật thông tin sản phẩm và các biến thể */
    ProductDetailResponse updateProduct(UUID productId, UpdateProductRequest request);

    /** Đổi trạng thái sản phẩm (Ẩn / Hoạt động / Ngừng kinh doanh) */
    ProductDetailResponse updateProductStatus(UUID productId, UpdateProductStatusRequest request);

    /** Xóa mềm sản phẩm (chuyển sang trạng thái DELETED) */
    void deleteProduct(UUID productId);

    // ── Variant (SKU) Operations ─────────────────────────────────────────────

    Page<VariantResponse> list(UUID productId, String keyword, VariantStatus status, boolean missingPrice,
                               String sortBy, String sortDir, String warehouseCode, Pageable pageable);

    VariantStatsResponse stats(UUID productId);

    VariantResponse create(UUID productId, VariantUpsertRequest request);

    List<VariantResponse> createMatrix(UUID productId, VariantMatrixRequest request);

    VariantResponse update(UUID productId, UUID variantId, VariantUpsertRequest request);

    VariantResponse changeStatus(UUID productId, UUID variantId, VariantStatusRequest request);

    VariantDeletionEligibilityResponse deletionEligibility(UUID productId, UUID variantId);

    void delete(UUID productId, UUID variantId, VariantDeleteRequest request);
}
