package com.aquasuite.ims.module.servicedesk.internal.repository;

import com.aquasuite.ims.module.servicedesk.internal.entity.TechnicalJob;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Truy xuất công việc kỹ thuật (FE-13). Tra cứu động (UC-59) dùng {@link JpaSpecificationExecutor}
 * để chỉ thêm điều kiện lọc khi có giá trị, tránh vấn đề kiểu dữ liệu của tham số null trên PostgreSQL.
 */
@Repository
public interface TechnicalJobRepository
        extends JpaRepository<TechnicalJob, UUID>, JpaSpecificationExecutor<TechnicalJob> {

    /** Kiểm tra đã tồn tại công việc kỹ thuật cho đơn hàng + loại công việc chưa (đảm bảo idempotent khi auto-sinh job). */
    boolean existsByOrderIdAndJobType(UUID orderId, TechnicalJobType jobType);
}
