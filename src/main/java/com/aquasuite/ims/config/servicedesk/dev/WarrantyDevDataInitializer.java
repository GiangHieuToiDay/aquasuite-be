package com.aquasuite.ims.config.servicedesk.dev;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.service.catalog.CatalogWarrantyLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto;
import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.dto.crm.response.CustomerSummaryDto;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.dto.iam.response.UserSummaryDto;
import com.aquasuite.ims.entity.servicedesk.WarrantyRecord;
import com.aquasuite.ims.enums.servicedesk.WarrantyStatus;
import com.aquasuite.ims.repository.servicedesk.WarrantyRecordRepository;
import com.aquasuite.ims.service.servicedesk.helper.WarrantyCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seed 2 thẻ bảo hành mẫu cho môi trường dev (UC-45) khi bảng warranty_records đang trống.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarrantyDevDataInitializer {

    private static final String OWNER_EMAIL = "owner@aquasuite.local";
    private static final String CUSTOMER_EMAIL = "customer@aquasuite.com";

    private final WarrantyRecordRepository warrantyRecordRepository;
    private final WarrantyCodeGenerator warrantyCodeGenerator;
    private final CatalogLookupService catalogApi;
    private final CatalogWarrantyLookupService catalogWarrantyApi;
    private final CustomerService crmApi;
    private final IamQueryService iamApi;
    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDevWarrantyRecords() {
        Set<String> profiles = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase).collect(Collectors.toSet());
        if (profiles.contains("prod") || !profiles.contains("dev")) {
            return;
        }
        if (warrantyRecordRepository.count() > 0) {
            return;
        }

        UUID ownerId = iamApi.findByEmail(OWNER_EMAIL).map(UserSummaryDto::id).orElse(null);
        UUID customerUserId = iamApi.findByEmail(CUSTOMER_EMAIL).map(UserSummaryDto::id).orElse(null);
        if (ownerId == null || customerUserId == null) {
            return;
        }

        CustomerSummaryDto customer = crmApi.getCustomerByUserId(customerUserId).orElse(null);
        List<CatalogSkuSummaryDto> activeSkus = catalogApi.getAllActiveSkus();
        if (customer == null || activeSkus.isEmpty()) {
            return;
        }

        List<CatalogWarrantyPolicyDto> policies = catalogWarrantyApi.getActiveWarrantyPolicies().stream()
                .filter(p -> p.warrantyMonths() != null && p.warrantyMonths() > 0)
                .toList();
        if (policies.isEmpty()) {
            return;
        }

        CatalogSkuSummaryDto firstSku = activeSkus.get(0);
        CatalogWarrantyPolicyDto activePolicy = policies.get(0);

        LocalDate today = LocalDate.now();
        OffsetDateTime now = OffsetDateTime.now();

        WarrantyRecord activeRecord = WarrantyRecord.builder()
                .warrantyCode(warrantyCodeGenerator.nextCode(today))
                .warrantyPolicyId(activePolicy.warrantyPolicyId())
                .customerId(customer.customerId())
                .variantId(firstSku.variantId())
                .serialNumber("SN-AQUA-DEV-0001")
                .startDate(today.minusMonths(2))
                .endDate(today.minusMonths(2).plusMonths(activePolicy.warrantyMonths()))
                .warrantyMonths(activePolicy.warrantyMonths())
                .warrantyTerms(activePolicy.warrantyTerms())
                .status(WarrantyStatus.ACTIVE)
                .createdBy(ownerId)
                .createdAt(now)
                .updatedAt(now)
                .build();

        LocalDate expiredStart = today.minusMonths(activePolicy.warrantyMonths() + 3);
        WarrantyRecord expiredRecord = WarrantyRecord.builder()
                .warrantyCode(warrantyCodeGenerator.nextCode(today))
                .warrantyPolicyId(activePolicy.warrantyPolicyId())
                .customerId(customer.customerId())
                .variantId(firstSku.variantId())
                .serialNumber("SN-AQUA-DEV-0002")
                .startDate(expiredStart)
                .endDate(expiredStart.plusMonths(activePolicy.warrantyMonths()))
                .warrantyMonths(activePolicy.warrantyMonths())
                .warrantyTerms(activePolicy.warrantyTerms())
                .status(WarrantyStatus.ACTIVE)
                .createdBy(ownerId)
                .createdAt(now)
                .updatedAt(now)
                .build();

        warrantyRecordRepository.saveAll(List.of(activeRecord, expiredRecord));
        log.info("Seeded 2 sample dev warranty records (ACTIVE & EXPIRED) for UC-45.");
    }
}

