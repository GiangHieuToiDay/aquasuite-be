package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload yêu cầu gộp danh sách sản phẩm từ giỏ hàng tạm (Guest Cart) vào tài khoản sau khi đăng nhập (UC-17).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeCartItemsRequest {

    @NotEmpty(message = "Danh sách sản phẩm gộp vào giỏ hàng không được trống")
    @Size(max = 50, message = "Tối đa 50 dòng sản phẩm mỗi lần đồng bộ giỏ hàng")
    private List<@Valid AddCartItemRequest> items;
}

