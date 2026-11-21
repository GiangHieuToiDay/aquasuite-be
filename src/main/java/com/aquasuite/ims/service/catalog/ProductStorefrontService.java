package com.aquasuite.ims.service.catalog;

import com.aquasuite.ims.dto.catalog.request.ProductSearchCriteria;
import com.aquasuite.ims.dto.catalog.response.ProductFacetResponse;
import com.aquasuite.ims.dto.catalog.response.StorefrontProductDetailResponse;
import com.aquasuite.ims.dto.catalog.response.StorefrontSearchResultResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductStorefrontService {

    /**
     * Tìm kiếm và lọc sản phẩm cho Storefront / POS với faceted filters, real-time stock
     * và thông báo MSG43 khi không tìm thấy sản phẩm.
     */
    StorefrontSearchResultResponse searchProducts(ProductSearchCriteria criteria, Pageable pageable);

    /**
     * Lấy dữ liệu siêu dữ liệu bộ lọc (Faceted Filter sidebar): danh mục, thương hiệu, khoảng giá, màu sắc, kích thước.
     */
    ProductFacetResponse getFacets();

    /**
     * Xem trang chi tiết sản phẩm toàn diện: bộ chọn biến thể (Variant selector),
     * trạng thái tồn kho thời gian thực (phân quyền Sales Staff vs Khách hàng),
     * bảng thông số kỹ thuật, bản vẽ CAD và sổ bảo hành.
     */
    StorefrontProductDetailResponse getProductDetail(UUID productId);
}
