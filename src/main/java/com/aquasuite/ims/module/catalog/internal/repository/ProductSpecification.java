package com.aquasuite.ims.module.catalog.internal.repository;

import com.aquasuite.ims.module.catalog.internal.entity.Product;
import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProductSpecification {

    public static Specification<Product> filter(
            String keyword,
            UUID categoryId,
            UUID brandId,
            ProductStatus status,
            ProductType productType,
            boolean excludeDeleted
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.trim().isEmpty()) {
                String searchPattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("productName")), searchPattern);
                Predicate codeMatch = cb.like(cb.lower(root.get("productCode")), searchPattern);
                predicates.add(cb.or(nameMatch, codeMatch));
            }

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }

            if (brandId != null) {
                predicates.add(cb.equal(root.get("brandId"), brandId));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else if (excludeDeleted) {
                predicates.add(cb.notEqual(root.get("status"), ProductStatus.DELETED));
            }

            if (productType != null) {
                predicates.add(cb.equal(root.get("productType"), productType));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
