package com.aquasuite.ims.module.finance.internal.service.impl;

import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.error.OrderPaymentErrorCode;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import com.aquasuite.ims.module.finance.internal.repository.OrderPaymentProofRepository;
import com.aquasuite.ims.module.finance.internal.service.OrderPaymentProofService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.share.exception.AppException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Lưu và đọc chứng từ chuyển khoản trong database, qua API có kiểm soát quyền. */
@Service
@RequiredArgsConstructor
public class OrderPaymentProofServiceImpl implements OrderPaymentProofService {
    /** Dung lượng tối đa của một chứng từ, tính bằng byte. */
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    /** Đọc và lưu dữ liệu chứng từ. */
    private final OrderPaymentProofRepository proofs;
    /** Kiểm tra khoản thu và trạng thái chuyển khoản. */
    private final TransactionRepository transactions;
    /** Khóa đơn trong transaction khi tải chứng từ lên. */
    private final SalesOrderApi sales;
    /** Xác định người tải chứng từ lên. */
    private final IamApi iam;

    /**
     * Lưu một chứng từ PNG/JPEG/PDF tối đa 5 MB cho giao dịch chuyển khoản PENDING.
     * @param orderId mã đơn sở hữu giao dịch
     * @param paymentId mã giao dịch cần bổ sung chứng từ
     * @param file tệp được tải lên
     * @throws IOException nếu không đọc được nội dung tệp
     * @throws AppException nếu giao dịch, tệp hoặc trạng thái không hợp lệ, hoặc đã có chứng từ
     */
    @Override
    @Transactional
    public void upload(UUID orderId, UUID paymentId, MultipartFile file) throws IOException {
        sales.lockOrderPaymentInfo(orderId).orElseThrow(() -> new AppException(OrderPaymentErrorCode.ORDER_NOT_FOUND));
        Transaction payment = transactions.findById(paymentId)
                .filter(t -> orderId.equals(t.getOrderId()) && "RECEIPT".equalsIgnoreCase(t.getTransactionType()))
                .orElseThrow(() -> new AppException(OrderPaymentErrorCode.PAYMENT_NOT_FOUND));
        if (!"BANK_TRANSFER".equalsIgnoreCase(payment.getPaymentMethod()) || !"PENDING".equalsIgnoreCase(payment.getStatus())) {
            throw new AppException(OrderPaymentErrorCode.PROOF_NOT_ALLOWED);
        }
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw new AppException(OrderPaymentErrorCode.INVALID_PROOF);
        byte[] content = file.getBytes();
        if (content.length == 0 || content.length > MAX_BYTES) throw new AppException(OrderPaymentErrorCode.INVALID_PROOF);
        String type = detectType(content);
        if (proofs.insertIfAbsent(paymentId, type, content, iam.getCurrentUserId()) == 0)
            throw new AppException(OrderPaymentErrorCode.PROOF_EXISTS);
    }

    /**
     * Đọc chứng từ nếu giao dịch thuộc đúng đơn được yêu cầu.
     * @param orderId mã đơn sở hữu giao dịch
     * @param paymentId mã giao dịch có chứng từ
     * @return loại nội dung và dữ liệu tệp
     * @throws AppException nếu không tìm thấy chứng từ phù hợp
     */
    @Override
    @Transactional(readOnly = true)
    public Proof read(UUID orderId, UUID paymentId) {
        return proofs.findByOrderAndPayment(orderId, paymentId)
                .map(proof -> new Proof(proof.getMimeType(), proof.getContent()))
                .orElseThrow(() -> new AppException(OrderPaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    /**
     * Nhận diện loại tệp qua các byte đầu, không dựa vào đuôi hoặc MIME client gửi.
     * @param data nội dung tệp cần kiểm tra
     * @return MIME của PNG, JPEG hoặc PDF
     * @throws AppException nếu chữ ký tệp không thuộc các loại hỗ trợ
     */
    static String detectType(byte[] data) {
        if (data.length >= 8 && Arrays.equals(Arrays.copyOf(data, 8),
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) return "image/png";
        if (data.length >= 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8 && (data[2] & 0xff) == 0xff) return "image/jpeg";
        if (data.length >= 5 && Arrays.equals(Arrays.copyOf(data, 5), "%PDF-".getBytes(StandardCharsets.US_ASCII))) return "application/pdf";
        throw new AppException(OrderPaymentErrorCode.INVALID_PROOF);
    }
}
