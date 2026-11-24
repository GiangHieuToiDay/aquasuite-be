package com.aquasuite.ims.repository.servicedesk;

import com.aquasuite.ims.entity.servicedesk.TechnicalJob;
import com.aquasuite.ims.enums.servicedesk.TechnicalJobStatus;
import com.aquasuite.ims.enums.servicedesk.TechnicalJobType;
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
 * Truy xuất công việc kỹ thuật (bảng technical_jobs). FE-13 và role TECHNICAL_STAFF đã bị loại bỏ (2026-11-24);
 * repository chỉ còn phục vụ hồ sơ thẻ bảo hành (UC-45) đọc lại các phiếu sửa chữa bảo hành đã có trong dữ liệu.
 */
@Repository
public interface TechnicalJobRepository
        extends JpaRepository<TechnicalJob, UUID>, JpaSpecificationExecutor<TechnicalJob> {

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
