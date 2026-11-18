package com.aquasuite.ims.module.servicedesk.internal.service.impl;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderItemSummaryDto;
import com.aquasuite.ims.module.servicedesk.internal.entity.WarrantyRecord;
import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyStatus;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyLookupResponse;
import com.aquasuite.ims.module.servicedesk.internal.repository.WarrantyRecordRepository;
import com.aquasuite.ims.module.servicedesk.internal.service.WarrantyLookupService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class WarrantyLookupServiceImpl implements WarrantyLookupService {

    WarrantyRecordRepository warrantyRecordRepository;
    IamApi iamApi;
    CrmApi crmApi;
    CatalogApi catalogApi;
    SalesOrderApi salesOrderApi;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<WarrantyLookupResponse> findMine(String keyword) {
        UUID userId = iamApi.getCurrentUserId();
        Optional<UUID> customerId = crmApi.getCustomerByUserId(userId).map(customer -> customer.customerId());
        if (customerId.isEmpty()) return List.of();
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        LocalDate today = LocalDate.now(clock);
        return warrantyRecordRepository.findByCustomerIdOrderByStartDateDesc(customerId.get()).stream()
                .map(record -> toResponse(record, today))
                .filter(response -> matches(response, normalizedKeyword))
                .toList();
    }

    private WarrantyLookupResponse toResponse(WarrantyRecord record, LocalDate today) {
        CatalogSkuSummaryDto sku = catalogApi.getSkuSummary(record.getVariantId()).orElse(null);
        SalesOrderItemSummaryDto orderItem = record.getOrderItemId() == null
                ? null
                : salesOrderApi.getOrderItemSummary(record.getOrderItemId()).orElse(null);
        String productName = firstText(orderItem == null ? null : orderItem.productName(), sku == null ? null : sku.productName(), "Sản phẩm");
        String variantName = firstText(orderItem == null ? null : orderItem.variantName(), sku == null ? null : sku.variantName(), null);
        String skuCode = firstText(orderItem == null ? null : orderItem.sku(), sku == null ? null : sku.sku(), null);
        boolean voided = record.getStatus() == WarrantyStatus.VOIDED;
        boolean active = !voided && record.getEndDate() != null && !today.isAfter(record.getEndDate());
        long remainingDays = active ? Math.max(0, ChronoUnit.DAYS.between(today, record.getEndDate())) : 0;
        String status = voided ? "VOIDED" : (active ? "ACTIVE" : "EXPIRED");
        return WarrantyLookupResponse.builder().warrantyRecordId(record.getWarrantyRecordId())
                .productName(productName).variantName(variantName).sku(skuCode).serialNumber(record.getSerialNumber())
                .orderCode(orderItem == null ? null : orderItem.orderCode()).warrantyTerms(record.getWarrantyTerms())
                .startDate(record.getStartDate()).endDate(record.getEndDate()).remainingDays(remainingDays)
                .status(status).claimAllowed(active).build();
    }

    private boolean matches(WarrantyLookupResponse record, String keyword) {
        return keyword == null || contains(record.serialNumber(), keyword) || contains(record.orderCode(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String firstText(String first, String fallback, String defaultValue) {
        if (StringUtils.hasText(first)) return first;
        if (StringUtils.hasText(fallback)) return fallback;
        return defaultValue;
    }
}
