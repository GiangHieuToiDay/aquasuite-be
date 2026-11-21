package com.aquasuite.ims.service.catalog;

import com.aquasuite.ims.enums.catalog.ProductStatus;
import com.aquasuite.ims.enums.catalog.ProductType;
import com.aquasuite.ims.dto.catalog.request.CreateProductRequest;
import com.aquasuite.ims.dto.catalog.request.UpdateProductRequest;
import com.aquasuite.ims.dto.catalog.request.UpdateProductStatusRequest;
import com.aquasuite.ims.dto.catalog.response.ProductDetailResponse;
import com.aquasuite.ims.dto.catalog.response.ProductSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service quản lý danh mục sản phẩm dành riêng cho Chủ cửa hàng (Shop Owner).
 */
public interface ProductShopOwnerService {

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

}
