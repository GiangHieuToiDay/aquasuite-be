package com.aquasuite.ims.share.excel.dto;

import java.io.Serializable;

public record ExcelRowError(
        int rowNumber,          // Số thứ tự dòng trên Excel (1-based, ví dụ: Dòng 14)
        String rowKey,          // Mã định danh dòng (VD: SKU "SP008354")
        String errorMessage     // Nội dung lỗi cụ thể
) implements Serializable {}

