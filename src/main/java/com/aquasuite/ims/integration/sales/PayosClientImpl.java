package com.aquasuite.ims.integration.sales;
import com.aquasuite.ims.config.PayosProperties;

import com.aquasuite.ims.exception.sales.SalesErrorCode;
import com.aquasuite.ims.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;

import java.util.List;

/**
 * Triển khai giao tiếp PayOS API bằng thư viện chính thức vn.payos:payos-java (2.0.2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayosClientImpl implements PayosClient {

    private final PayosProperties properties;

    @Override
    public PaymentLinkResult createPaymentLink(CreatePaymentCommand command) {
        PayOS payOS = buildSdkClient();
        try {
            List<PaymentLinkItem> sdkItems = command.items() == null
                    ? List.of()
                    : command.items().stream()
                    .map(i -> PaymentLinkItem.builder()
                            .name(i.name())
                            .quantity(Math.max(1, i.quantity()))
                            .price(Math.max(0L, i.price()))
                            .unit(i.unit())
                            .build())
                    .toList();

            CreatePaymentLinkRequest.CreatePaymentLinkRequestBuilder reqBuilder = CreatePaymentLinkRequest.builder()
                    .orderCode(command.orderCode())
                    .amount(command.amount())
                    .description(command.description())
                    .returnUrl(command.returnUrl())
                    .cancelUrl(command.cancelUrl());

            if (!sdkItems.isEmpty()) {
                reqBuilder.items(sdkItems);
            }
            if (isNotBlank(command.buyerName())) {
                reqBuilder.buyerName(command.buyerName());
            }
            if (isNotBlank(command.buyerEmail())) {
                reqBuilder.buyerEmail(command.buyerEmail());
            }
            if (isNotBlank(command.buyerPhone())) {
                reqBuilder.buyerPhone(command.buyerPhone());
            }
            if (isNotBlank(command.buyerAddress())) {
                reqBuilder.buyerAddress(command.buyerAddress());
            }

            CreatePaymentLinkResponse res = payOS.paymentRequests().create(reqBuilder.build());
            return new PaymentLinkResult(
                    res.getOrderCode() != null ? res.getOrderCode() : command.orderCode(),
                    res.getPaymentLinkId(),
                    res.getCheckoutUrl(),
                    res.getQrCode(),
                    res.getBin(),
                    res.getAccountNumber(),
                    res.getAccountName(),
                    res.getAmount() != null ? res.getAmount() : command.amount(),
                    res.getDescription() != null ? res.getDescription() : command.description(),
                    res.getStatus() != null ? res.getStatus().name() : "PENDING"
            );
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("PayOS createPaymentLink failed for payosOrderCode={}: {}", command.orderCode(), ex.getMessage(), ex);
            throw new AppException(SalesErrorCode.PAYOS_API_ERROR, ex.getMessage());
        }
    }

    @Override
    public PaymentLinkStatusResult getPaymentLinkInfo(long payosOrderCode) {
        PayOS payOS = buildSdkClient();
        try {
            PaymentLink link = payOS.paymentRequests().get(payosOrderCode);
            long amount = link.getAmount() != null ? link.getAmount() : 0L;
            long amountPaid = link.getAmountPaid() != null ? link.getAmountPaid() : 0L;
            long amountRemaining = link.getAmountRemaining() != null ? link.getAmountRemaining() : Math.max(0L, amount - amountPaid);
            return new PaymentLinkStatusResult(
                    link.getOrderCode() != null ? link.getOrderCode() : payosOrderCode,
                    link.getId(),
                    amount,
                    amountPaid,
                    amountRemaining,
                    link.getStatus() != null ? link.getStatus().name() : "PENDING"
            );
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("PayOS getPaymentLinkInfo failed for payosOrderCode={}: {}", payosOrderCode, ex.getMessage(), ex);
            throw new AppException(SalesErrorCode.PAYOS_API_ERROR, ex.getMessage());
        }
    }

    @Override
    public PaymentLinkStatusResult cancelPaymentLink(long payosOrderCode, String reason) {
        PayOS payOS = buildSdkClient();
        try {
            PaymentLink link = isNotBlank(reason)
                    ? payOS.paymentRequests().cancel(payosOrderCode, reason)
                    : payOS.paymentRequests().cancel(payosOrderCode);
            long amount = link.getAmount() != null ? link.getAmount() : 0L;
            long amountPaid = link.getAmountPaid() != null ? link.getAmountPaid() : 0L;
            long amountRemaining = link.getAmountRemaining() != null ? link.getAmountRemaining() : Math.max(0L, amount - amountPaid);
            return new PaymentLinkStatusResult(
                    link.getOrderCode() != null ? link.getOrderCode() : payosOrderCode,
                    link.getId(),
                    amount,
                    amountPaid,
                    amountRemaining,
                    link.getStatus() != null ? link.getStatus().name() : "CANCELLED"
            );
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("PayOS cancelPaymentLink failed for payosOrderCode={}: {}", payosOrderCode, ex.getMessage(), ex);
            throw new AppException(SalesErrorCode.PAYOS_API_ERROR, ex.getMessage());
        }
    }

    private PayOS buildSdkClient() {
        if (!properties.isConfigured()) {
            throw new AppException(SalesErrorCode.PAYOS_CONFIG_MISSING);
        }
        ClientOptions.ClientOptionsBuilder builder = ClientOptions.builder()
                .clientId(properties.getClientId().trim())
                .apiKey(properties.getApiKey().trim())
                .checksumKey(properties.getChecksumKey().trim());
        if (isNotBlank(properties.getBaseUrl())) {
            builder.baseURL(properties.getBaseUrl().trim());
        }
        return new PayOS(builder.build());
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}

