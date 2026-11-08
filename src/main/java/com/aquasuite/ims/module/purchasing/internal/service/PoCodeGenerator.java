package com.aquasuite.ims.module.purchasing.internal.service;

import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class PoCodeGenerator {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * Tạo mã đơn mua hàng duy nhất theo định dạng PO-yyyyMM-xxxx (ví dụ: PO-202611-0001).
     */
    public synchronized String generatePoCode() {
        String monthPart = LocalDate.now().format(MONTH_FORMATTER);
        String prefix = "PO-" + monthPart + "-";

        long currentCount = purchaseOrderRepository.countByPoCodeStartingWith(prefix);
        long nextSequence = currentCount + 1;

        String code = String.format("%s%04d", prefix, nextSequence);
        while (purchaseOrderRepository.existsByPoCode(code)) {
            nextSequence++;
            code = String.format("%s%04d", prefix, nextSequence);
        }

        return code;
    }
}