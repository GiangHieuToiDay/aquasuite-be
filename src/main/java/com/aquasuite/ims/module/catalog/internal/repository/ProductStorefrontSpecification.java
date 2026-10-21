package com.aquasuite.ims.module.catalog.internal.repository;

import com.aquasuite.ims.module.catalog.internal.entity.Product;
import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class ProductStorefrontSpecification {

    public static Specification<Product> filter(
            String keyword,
            UUID categoryId,
            UUID brandId,
            Collection<UUID> variantMatchedProductIds,
            Collection<UUID> constrainedProductIds
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Chỉ hiển thị sản phẩm đang ACTIVE cho Storefront / POS
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));

            // 2. Lọc theo Danh mục
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }

            // 3. Lọc theo Thương hiệu
            if (brandId != null) {
                predicates.add(cb.equal(root.get("brandId"), brandId));
            }

            // 4. Nếu có danh sách productId bị giới hạn (từ bộ lọc giá, tồn kho, màu sắc, kích thước...)
            if (constrainedProductIds != null) {
                if (constrainedProductIds.isEmpty()) {
                    // Không có sản phẩm nào thỏa mãn ràng buộc biến thể/kho
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("productId").in(constrainedProductIds));
                }
            }

            // 5. Tìm kiếm toàn văn theo từ khóa (Keyword full-text search)
            if (keyword != null && !keyword.trim().isEmpty()) {
                String cleanKw = keyword.trim().toLowerCase();
                String pattern = "%" + cleanKw + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("productName")), pattern);
                Predicate codeMatch = cb.like(cb.lower(root.get("productCode")), pattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);

                Predicate productSearch = cb.or(nameMatch, codeMatch, descMatch);

                // Hỗ trợ tìm kiếm theo từng từ khóa (token search) cho các từ khóa ghép (ví dụ: "TOTO smart toilet 305mm")
                String[] tokens = cleanKw.split("\\s+");
                if (tokens.length > 1) {
                    List<Predicate> tokenPredicates = new ArrayList<>();
                    for (String token : tokens) {
                        if (token.length() >= 2) {
                            String tokenPattern = "%" + token + "%";
                            tokenPredicates.add(cb.or(
                                    cb.like(cb.lower(root.get("productName")), tokenPattern),
                                    cb.like(cb.lower(root.get("productCode")), tokenPattern),
                                    cb.like(cb.lower(root.get("description")), tokenPattern)
                            ));
                        }
                    }
                    if (!tokenPredicates.isEmpty()) {
                        productSearch = cb.or(productSearch, cb.or(tokenPredicates.toArray(new Predicate[0])));
                    }
                }

                if (variantMatchedProductIds != null && !variantMatchedProductIds.isEmpty()) {
                    Predicate variantMatch = root.get("productId").in(variantMatchedProductIds);
                    predicates.add(cb.or(productSearch, variantMatch));
                } else {
                    predicates.add(productSearch);
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
