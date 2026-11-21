package com.aquasuite.ims.service.catalog;

import com.aquasuite.ims.dto.catalog.request.CreateBrandRequest;
import com.aquasuite.ims.dto.catalog.request.UpdateBrandRequest;
import com.aquasuite.ims.dto.catalog.response.BrandResponse;
import com.aquasuite.ims.dto.catalog.response.BrandBriefResponse;
import com.aquasuite.ims.common.PageQuery;
import com.aquasuite.ims.common.PageResponse;

import java.util.List;
import java.util.UUID;

public interface BrandService {

    /** Lấy danh sách rút gọn của các thương hiệu đang hoạt động. */
    List<BrandBriefResponse> getActiveBrandOptions();

    /** Tìm kiếm, lọc trạng thái và phân trang thương hiệu. */
    PageResponse<BrandResponse> listBrands(PageQuery pageQuery, String search, String status);

    /** Lấy chi tiết thương hiệu chưa bị xóa mềm. */
    BrandResponse getBrandDetail(UUID brandId);

    /** Chuẩn hóa dữ liệu và tạo thương hiệu có mã duy nhất. */
    BrandResponse createBrand(CreateBrandRequest request);

    /** Cập nhật thương hiệu sau khi kiểm tra trùng mã và tên. */
    BrandResponse updateBrand(UUID brandId, UpdateBrandRequest request);

    /** Xóa mềm thương hiệu khi không còn ràng buộc sản phẩm. */
    void deleteBrand(UUID brandId);
}
