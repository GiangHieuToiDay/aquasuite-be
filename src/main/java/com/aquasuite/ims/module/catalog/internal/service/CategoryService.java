package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.payload.req.CreateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.CategoryResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.CategoryBriefResponse;
import com.aquasuite.ims.share.dto.PageQuery;
import com.aquasuite.ims.share.dto.PageResponse;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    /** Lấy danh sách rút gọn của các danh mục đang hoạt động. */
    List<CategoryBriefResponse> getActiveCategoryOptions();

    /** Tìm kiếm, lọc trạng thái và phân trang danh mục. */
    PageResponse<CategoryResponse> listCategories(PageQuery pageQuery, String search, String status);

    /** Lấy chi tiết danh mục chưa bị xóa mềm. */
    CategoryResponse getCategoryDetail(UUID categoryId);

    /** Tạo danh mục mới với mã duy nhất và quan hệ cha hợp lệ. */
    CategoryResponse createCategory(CreateCategoryRequest request);

    /** Cập nhật danh mục đồng thời bảo vệ cấu trúc cây khỏi chu trình. */
    CategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest request);

    /** Xóa mềm danh mục khi không còn ràng buộc phụ thuộc. */
    void deleteCategory(UUID categoryId);
}
