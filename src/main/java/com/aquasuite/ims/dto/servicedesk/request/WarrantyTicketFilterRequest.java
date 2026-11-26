package com.aquasuite.ims.dto.servicedesk.request;

import com.aquasuite.ims.enums.servicedesk.WarrantyTicketStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarrantyTicketFilterRequest {
    private WarrantyTicketStatus status;
    @Min(value = 0, message = "Trang phải lớn hơn hoặc bằng 0.")
    private Integer page;
    @Min(value = 1, message = "Kích thước trang phải từ 1 trở lên.")
    @Max(value = 100, message = "Kích thước trang tối đa 100.")
    private Integer size;
}

