package com.aquasuite.ims.module.finance.internal.payload.response;

import java.util.List;

/**
 * Trang danh sách khoản chuyển khoản đang chờ xác minh (UC-70).
 *
 * @param content       các khoản trong trang hiện tại
 * @param page          chỉ số trang (bắt đầu từ 0)
 * @param size          kích thước trang
 * @param totalElements tổng số khoản phù hợp bộ lọc
 * @param totalPages    tổng số trang
 */
public record PaymentVerificationPageResponse(
        List<PaymentVerificationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
