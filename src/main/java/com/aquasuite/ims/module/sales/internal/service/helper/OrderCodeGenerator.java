package com.aquasuite.ims.module.sales.internal.service.helper;

import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class OrderCodeGenerator {

    private final OrderRepository orderRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Tạo mã đơn hàng duy nhất theo định dạng SO-YYYYMMDD-XXXX (ví dụ: SO-20261031-0001).
     */
    public String generateOrderCode() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        String prefix = "SO-" + datePart + "-";

        long currentCount = orderRepository.countByOrderCodeStartingWith(prefix);
        long nextSequence = currentCount + 1;

        String code = String.format("%s%04d", prefix, nextSequence);
        while (orderRepository.existsByOrderCode(code)) {
            nextSequence++;
            code = String.format("%s%04d", prefix, nextSequence);
        }

        return code;
    }
}
