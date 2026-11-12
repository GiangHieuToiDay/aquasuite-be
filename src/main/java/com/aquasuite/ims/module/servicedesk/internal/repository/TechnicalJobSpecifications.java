package com.aquasuite.ims.module.servicedesk.internal.repository;

import com.aquasuite.ims.module.servicedesk.internal.entity.TechnicalJob;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Xây dựng điều kiện tra cứu công việc kỹ thuật (UC-59). Mỗi điều kiện chỉ được thêm khi có giá trị,
 * nên không phát sinh tham số null kiểu không xác định.
 */
public final class TechnicalJobSpecifications {

    private TechnicalJobSpecifications() {
    }

    public static Specification<TechnicalJob> filter(UUID technicianId,
                                                     TechnicalJobStatus status,
                                                     TechnicalJobType jobType,
                                                     OffsetDateTime from,
                                                     OffsetDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (technicianId != null) {
                predicates.add(cb.equal(root.get("technicianId"), technicianId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (jobType != null) {
                predicates.add(cb.equal(root.get("jobType"), jobType));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
