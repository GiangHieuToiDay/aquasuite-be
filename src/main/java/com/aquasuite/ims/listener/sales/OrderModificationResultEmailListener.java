package com.aquasuite.ims.listener.sales;

import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.event.sales.OrderModificationResultEmailRequestedEvent;
import com.aquasuite.ims.integration.sales.OrderModificationEmailNotificationPort;
import com.aquasuite.ims.repository.sales.OrderModificationRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderModificationResultEmailListener {
    private final OrderModificationRequestRepository requests;
    private final CustomerService crmApi;
    private final OrderModificationEmailNotificationPort email;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(OrderModificationResultEmailRequestedEvent event) {
        try {
            var request = requests.findById(event.requestId()).orElse(null);
            var customer = crmApi.getCustomerById(event.customerId()).orElse(null);
            if (request == null || customer == null || customer.email() == null || customer.email().isBlank()) {
                log.warn("UC-26 result email {} not delivered: request or customer email unavailable", event.requestId());
                return;
            }
            String subject = "[AquaSuite] Kết quả yêu cầu điều chỉnh đơn hàng";
            String body = "Yêu cầu điều chỉnh đơn hàng của quý khách hiện có trạng thái "
                    + request.getRequestStatus().name() + "."
                    + (request.getDecisionNote() == null ? "" : " Ghi chú: " + request.getDecisionNote());
            email.send(customer.email().trim(), subject, body);
            log.info("UC-26 result email delivered for request {}", event.requestId());
        } catch (Exception exception) {
            // A notification error must not undo the already committed UC-26 decision.
            log.error("UC-26 result email delivery failed for request {}", event.requestId(), exception);
        }
    }
}
