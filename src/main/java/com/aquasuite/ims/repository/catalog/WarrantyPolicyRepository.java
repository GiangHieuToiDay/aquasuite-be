package com.aquasuite.ims.repository.catalog;

import com.aquasuite.ims.entity.catalog.WarrantyPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface WarrantyPolicyRepository {

    Optional<WarrantyPolicy> findById(UUID id);

    Optional<WarrantyPolicy> findByPolicyCodeIgnoreCase(String policyCode);

    List<WarrantyPolicy> findByStatusOrderByWarrantyMonthsAscPolicyNameAsc(String status);

    Optional<WarrantyPolicy> findFirstByBrandIdAndCategoryIdAndStatus(
            UUID brandId,
            UUID categoryId,
            String status
    );

    long count();

    WarrantyPolicy save(WarrantyPolicy policy);

    List<WarrantyPolicy> saveAll(Iterable<WarrantyPolicy> policies);
}

@Repository("catalogWarrantyPolicyRepository")
class JdbcCatalogWarrantyPolicyRepository implements WarrantyPolicyRepository {

    private static final String BASE_SELECT = """
            SELECT warranty_policy_id,
                   policy_code,
                   policy_name,
                   brand_id,
                   category_id,
                   warranty_period_months,
                   coverage_conditions,
                   status,
                   created_at,
                   updated_at
            FROM public.warranty_policies
            """;

    private static final RowMapper<WarrantyPolicy> ROW_MAPPER = (rs, rowNum) -> WarrantyPolicy.builder()
            .warrantyPolicyId(rs.getObject("warranty_policy_id", UUID.class))
            .policyCode(rs.getString("policy_code"))
            .policyName(rs.getString("policy_name"))
            .brandId(rs.getObject("brand_id", UUID.class))
            .categoryId(rs.getObject("category_id", UUID.class))
            .warrantyMonths(rs.getObject("warranty_period_months", Integer.class))
            .warrantyTerms(rs.getString("coverage_conditions"))
            .status(rs.getString("status"))
            .createdAt(rs.getObject("created_at", OffsetDateTime.class))
            .updatedAt(rs.getObject("updated_at", OffsetDateTime.class))
            .build();

    private final JdbcTemplate jdbcTemplate;

    JdbcCatalogWarrantyPolicyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<WarrantyPolicy> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                BASE_SELECT + " WHERE warranty_policy_id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    @Override
    public Optional<WarrantyPolicy> findByPolicyCodeIgnoreCase(String policyCode) {
        if (policyCode == null || policyCode.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                BASE_SELECT + " WHERE lower(policy_code) = lower(?)",
                ROW_MAPPER,
                policyCode.trim()
        ).stream().findFirst();
    }

    @Override
    public List<WarrantyPolicy> findByStatusOrderByWarrantyMonthsAscPolicyNameAsc(String status) {
        if (status == null || status.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query(
                BASE_SELECT + " WHERE status = ? ORDER BY warranty_period_months ASC, policy_name ASC",
                ROW_MAPPER,
                status.trim()
        );
    }

    @Override
    public Optional<WarrantyPolicy> findFirstByBrandIdAndCategoryIdAndStatus(
            UUID brandId,
            UUID categoryId,
            String status
    ) {
        if (brandId == null || categoryId == null || status == null || status.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                BASE_SELECT + " WHERE brand_id = ? AND category_id = ? AND status = ? "
                        + "ORDER BY created_at DESC, warranty_policy_id ASC LIMIT 1",
                ROW_MAPPER,
                brandId,
                categoryId,
                status.trim()
        ).stream().findFirst();
    }

    @Override
    public long count() {
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.warranty_policies",
                Long.class
        );
        return total == null ? 0L : total;
    }

    @Override
    public WarrantyPolicy save(WarrantyPolicy policy) {
        Objects.requireNonNull(policy, "WarrantyPolicy must not be null");
        policy.prePersist();
        int months = (policy.getWarrantyMonths() != null && policy.getWarrantyMonths() > 0)
                ? policy.getWarrantyMonths()
                : 12;
        String coverage = (policy.getWarrantyTerms() != null && !policy.getWarrantyTerms().isBlank())
                ? policy.getWarrantyTerms().trim()
                : policy.getPolicyName();

        Long existing = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.warranty_policies WHERE warranty_policy_id = ?",
                Long.class,
                policy.getWarrantyPolicyId()
        );
        if (existing != null && existing > 0) {
            policy.preUpdate();
            jdbcTemplate.update(
                    """
                    UPDATE public.warranty_policies
                    SET policy_code = ?,
                        policy_name = ?,
                        brand_id = ?,
                        category_id = ?,
                        warranty_period_months = ?,
                        coverage_conditions = ?,
                        status = ?,
                        updated_at = ?
                    WHERE warranty_policy_id = ?
                    """,
                    policy.getPolicyCode(),
                    policy.getPolicyName(),
                    policy.getBrandId(),
                    policy.getCategoryId(),
                    months,
                    coverage,
                    policy.getStatus(),
                    policy.getUpdatedAt(),
                    policy.getWarrantyPolicyId()
            );
            return policy;
        }

        UUID actorId = jdbcTemplate.query(
                "SELECT id FROM public.users ORDER BY created_at ASC LIMIT 1",
                (rs, rowNum) -> rs.getObject("id", UUID.class)
        ).stream().findFirst().orElseThrow(() -> new IllegalStateException(
                "Cannot insert into public.warranty_policies because no user exists for created_by"));

        jdbcTemplate.update(
                """
                INSERT INTO public.warranty_policies (
                    warranty_policy_id,
                    policy_code,
                    policy_name,
                    brand_id,
                    category_id,
                    warranty_period_months,
                    coverage_conditions,
                    status,
                    created_by,
                    created_at,
                    updated_by,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                policy.getWarrantyPolicyId(),
                policy.getPolicyCode(),
                policy.getPolicyName(),
                policy.getBrandId(),
                policy.getCategoryId(),
                months,
                coverage,
                policy.getStatus(),
                actorId,
                policy.getCreatedAt(),
                actorId,
                policy.getUpdatedAt()
        );
        return policy;
    }

    @Override
    public List<WarrantyPolicy> saveAll(Iterable<WarrantyPolicy> policies) {
        Objects.requireNonNull(policies, "policies must not be null");
        List<WarrantyPolicy> saved = new ArrayList<>();
        for (WarrantyPolicy policy : policies) {
            saved.add(save(policy));
        }
        return saved;
    }
}
