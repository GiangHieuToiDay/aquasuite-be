package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.enums.ProductType;
import com.aquasuite.ims.module.catalog.internal.payload.req.CreateProductRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateProductRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateProductStatusRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.BrandBriefResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.CategoryBriefResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.ProductDetailResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.ProductSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProductAdminService {

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

    /** Lấy danh sách danh mục đang hoạt động (cho dropdown filter & form) */
    List<CategoryBriefResponse> getActiveCategories();

    /** Lấy danh sách thương hiệu đang hoạt động (cho dropdown filter & form) */
    List<BrandBriefResponse> getActiveBrands();
}
