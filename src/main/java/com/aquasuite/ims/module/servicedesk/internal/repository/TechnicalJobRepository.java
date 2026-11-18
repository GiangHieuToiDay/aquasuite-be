package com.aquasuite.ims.module.servicedesk.internal.repository;

import com.aquasuite.ims.module.servicedesk.internal.entity.TechnicalJob;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
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

    /** Lấy lịch sử công việc kỹ thuật gắn với một thẻ bảo hành (UC-45 dossier). */
    List<TechnicalJob> findByWarrantyRecordIdOrderByCreatedAtDesc(UUID warrantyRecordId);

    /** Kiểm tra một thẻ bảo hành có đang có phiếu sửa chữa bảo hành chưa hoàn tất hay không. */
    boolean existsByWarrantyRecordIdAndJobTypeAndStatusIn(
            UUID warrantyRecordId,
            TechnicalJobType jobType,
            Collection<TechnicalJobStatus> statuses
    );

    /** Truy vấn hàng loạt các warrantyRecordId đang có phiếu sửa chữa bảo hành chưa hoàn tất (tránh N+1). */
    @Query("""
        SELECT DISTINCT j.warrantyRecordId FROM TechnicalJob j
        WHERE j.warrantyRecordId IN :recordIds
          AND j.jobType = :jobType
          AND j.status IN :statuses
    """)
    Set<UUID> findWarrantyRecordIdsWithOpenJob(
            @Param("recordIds") Collection<UUID> recordIds,
            @Param("jobType") TechnicalJobType jobType,
            @Param("statuses") Collection<TechnicalJobStatus> statuses
    );
}
