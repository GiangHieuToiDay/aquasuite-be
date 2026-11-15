package com.aquasuite.ims.module.catalog.internal.config;

import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import com.aquasuite.ims.module.catalog.internal.entity.WarrantyPolicy;
import com.aquasuite.ims.module.catalog.internal.repository.WarrantyPolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dev-only initializer gắn chính sách bảo hành mẫu cho các SKU dev chưa có warranty_policy_id
 * (để đội FE/BE thử ngay luồng UC-45 mà không cần chờ UI gán chính sách của Catalog).
 */
@Slf4j
@Component
public class CatalogWarrantyDevInitializer {

    private final com.aquasuite.ims.module.catalog.internal.repository.ProductVariantRepository variantRepository;
    private final WarrantyPolicyRepository warrantyPolicyRepository;
    private final Environment environment;

    public CatalogWarrantyDevInitializer(
            com.aquasuite.ims.module.catalog.internal.repository.ProductVariantRepository variantRepository,
            WarrantyPolicyRepository warrantyPolicyRepository,
            Environment environment) {
        this.variantRepository = variantRepository;
        this.warrantyPolicyRepository = warrantyPolicyRepository;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void linkSamplePoliciesOnDev() {
        Set<String> profiles = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase).collect(Collectors.toSet());
        if (profiles.contains("prod") || !profiles.contains("dev")) {
            return;
        }
        WarrantyPolicy default24m = warrantyPolicyRepository.findByPolicyCodeIgnoreCase("WP-24M").orElse(null);
        WarrantyPolicy ceramic120m = warrantyPolicyRepository.findByPolicyCodeIgnoreCase("WP-120M").orElse(null);
        if (default24m == null) {
            return;
        }
        List<ProductVariant> unlinked = variantRepository.findAll().stream()
                .filter(v -> v.getWarrantyPolicyId() == null)
                .toList();
        if (unlinked.isEmpty()) {
            return;
        }
        for (ProductVariant variant : unlinked) {
            boolean isCeramic = variant.getSku() != null && variant.getSku().toUpperCase().contains("CS886");
            variant.setWarrantyPolicyId(isCeramic && ceramic120m != null
                    ? ceramic120m.getWarrantyPolicyId()
                    : default24m.getWarrantyPolicyId());
        }
        variantRepository.saveAll(unlinked);
        log.info("Linked default dev warranty policies to {} unconfigured catalog variants.", unlinked.size());
    }
}

