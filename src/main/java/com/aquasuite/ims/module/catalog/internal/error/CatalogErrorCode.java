package com.aquasuite.ims.module.catalog.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CatalogErrorCode implements BaseErrorCode {

    CATEGORY_NOT_FOUND("CAT_001", "Không tìm thấy danh mục yêu cầu", HttpStatus.NOT_FOUND),
    BRAND_NOT_FOUND("CAT_002", "Không tìm thấy thương hiệu yêu cầu", HttpStatus.NOT_FOUND),
    INVALID_CATEGORY_CODE("CAT_003", "Mã danh mục không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_BRAND_CODE("CAT_004", "Mã thương hiệu không hợp lệ", HttpStatus.BAD_REQUEST),
    CATEGORY_CODE_DUPLICATE("CAT_005", "Mã danh mục đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    BRAND_CODE_DUPLICATE("CAT_006", "Mã thương hiệu đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    CATEGORY_NAME_DUPLICATE("CAT_007", "Tên danh mục đã tồn tại trong cùng cấp cha", HttpStatus.CONFLICT),
    BRAND_NAME_DUPLICATE("CAT_008", "Tên thương hiệu đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    CATEGORY_PARENT_NOT_FOUND("CAT_009", "Không tìm thấy danh mục cha yêu cầu", HttpStatus.NOT_FOUND),
    CATEGORY_SELF_PARENT("CAT_010", "Danh mục không thể là cha của chính nó", HttpStatus.BAD_REQUEST),
    CATEGORY_CYCLE("CAT_011", "Không thể gán danh mục cha tạo thành vòng lặp phân cấp", HttpStatus.CONFLICT),
    CATEGORY_HIERARCHY_CONFLICT("CAT_012", "Cấu trúc phân cấp danh mục bị xung đột hoặc không hợp lệ", HttpStatus.CONFLICT),
    CATEGORY_HAS_CHILDREN("CAT_013", "Không thể xóa danh mục đang có danh mục con", HttpStatus.CONFLICT),
    CATEGORY_STATUS_INVALID("CAT_014", "Trạng thái danh mục không hợp lệ", HttpStatus.BAD_REQUEST),
    BRAND_STATUS_INVALID("CAT_015", "Trạng thái thương hiệu không hợp lệ", HttpStatus.BAD_REQUEST),
    CATEGORY_REFERENCED("CAT_016", "Danh mục đang được tham chiếu bởi sản phẩm", HttpStatus.CONFLICT),
    BRAND_REFERENCED("CAT_017", "Thương hiệu đang được tham chiếu bởi sản phẩm", HttpStatus.CONFLICT),
    PRODUCT_NOT_FOUND("CAT_018", "Không tìm thấy sản phẩm yêu cầu", HttpStatus.NOT_FOUND),
    PRODUCT_CODE_ALREADY_EXISTS("CAT_019", "Mã sản phẩm đã tồn tại trong hệ thống", HttpStatus.CONFLICT);



    private final String code;
    private final String message;
    private final HttpStatus status;
}
