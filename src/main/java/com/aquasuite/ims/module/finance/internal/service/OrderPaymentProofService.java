package com.aquasuite.ims.module.finance.internal.service;

import java.io.IOException;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** Các thao tác lưu và tải chứng từ thanh toán chuyển khoản. */
public interface OrderPaymentProofService {
    /**
     * Nội dung chứng từ được trả cho controller.
     * @param mimeType loại nội dung của tệp
     * @param content dữ liệu nhị phân của tệp
     */
    record Proof(String mimeType, byte[] content) {}

    /**
     * Lưu chứng từ của một khoản chuyển khoản đang chờ xác minh.
     * @param orderId mã đơn sở hữu khoản thu
     * @param paymentId mã giao dịch cần bổ sung chứng từ
     * @param file tệp chứng từ
     * @throws IOException nếu không đọc được tệp
     */
    void upload(UUID orderId, UUID paymentId, MultipartFile file) throws IOException;

    /**
     * Tải chứng từ của giao dịch thuộc đúng đơn được yêu cầu.
     * @param orderId mã đơn sở hữu khoản thu
     * @param paymentId mã giao dịch có chứng từ
     * @return nội dung và loại tệp
     */
    Proof read(UUID orderId, UUID paymentId);
}
