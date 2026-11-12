package com.aquasuite.ims.module.servicedesk.internal.service.impl;

import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.CreateTechnicalJobRequest;
import com.aquasuite.ims.module.servicedesk.internal.entity.TechnicalJob;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import com.aquasuite.ims.module.servicedesk.internal.error.ServicedeskErrorCode;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.CompleteTechnicalJobRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.TechnicalJobDetailResponse;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.TechnicalJobListItemResponse;
import com.aquasuite.ims.module.servicedesk.internal.repository.TechnicalJobRepository;
import com.aquasuite.ims.module.servicedesk.internal.repository.TechnicalJobSpecifications;
import com.aquasuite.ims.module.servicedesk.internal.service.TechnicalJobService;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TechnicalJobServiceImpl implements TechnicalJobService {

    private static final String SHOP_OWNER_ROLE = "SHOP_OWNER";
    private static final String TECHNICAL_STAFF_ROLE = "TECHNICAL_STAFF";

    private final TechnicalJobRepository technicalJobRepository;
    private final IamApi iamApi;
    private final CrmApi crmApi;

    @Override
    @Transactional(readOnly = true)
    public Page<TechnicalJobListItemResponse> searchJobs(TechnicalJobStatus status,
                                                         TechnicalJobType jobType,
                                                         OffsetDateTime from,
                                                         OffsetDateTime to,
                                                         Pageable pageable) {
        // Chủ cửa hàng xem toàn bộ (technicianId = null); kỹ thuật viên chỉ thấy công việc của mình.
        UUID scopedTechnicianId = isShopOwner() ? null : currentUserId();
        return technicalJobRepository
                .findAll(TechnicalJobSpecifications.filter(scopedTechnicianId, status, jobType, from, to), pageable)
                .map(this::toListItem);
    }

    @Override
    @Transactional(readOnly = true)
    public TechnicalJobDetailResponse getJobDetail(UUID technicalJobId) {
        TechnicalJob job = loadAndAuthorize(technicalJobId);
        return toDetail(job);
    }

    @Override
    @Transactional
    public TechnicalJobDetailResponse createJob(CreateTechnicalJobRequest request) {
        // Kiểm tra khách hàng tồn tại trong CRM (liên kết chéo module qua public API).
        if (crmApi.getCustomerById(request.customerId()).isEmpty()) {
            throw new AppException(ServicedeskErrorCode.CUSTOMER_NOT_FOUND, request.customerId());
        }
        // Nếu gán sẵn kỹ thuật viên thì phải hợp lệ (vai trò TECHNICAL_STAFF).
        if (request.technicianId() != null) {
            requireTechnician(request.technicianId());
        }
        TechnicalJob job = TechnicalJob.builder()
                .customerId(request.customerId())
                .technicianId(request.technicianId())
                .createdBy(currentUserId())
                .jobType(request.jobType())
                .status(TechnicalJobStatus.ASSIGNED)
                .serviceAddress(request.serviceAddress().trim())
                .issueDescription(request.issueDescription().trim())
                .scheduledAt(request.scheduledAt())
                .orderId(request.orderId())
                .warrantyRecordId(request.warrantyRecordId())
                .build();
        return toDetail(technicalJobRepository.save(job));
    }

    @Override
    @Transactional
    public TechnicalJobDetailResponse assignJob(UUID technicalJobId, UUID technicianId, OffsetDateTime scheduledAt) {
        TechnicalJob job = technicalJobRepository.findById(technicalJobId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.TECHNICAL_JOB_NOT_FOUND, technicalJobId));

        TechnicalJobStatus status = job.getStatus();
        if (status != TechnicalJobStatus.ASSIGNED && status != TechnicalJobStatus.REJECTED) {
            throw new AppException(ServicedeskErrorCode.JOB_NOT_ASSIGNABLE, String.valueOf(status));
        }
        requireTechnician(technicianId);

        job.setTechnicianId(technicianId);
        // Đặt/cập nhật lịch hẹn nếu Chủ cửa hàng nhập; để trống thì giữ nguyên lịch cũ.
        if (scheduledAt != null) {
            job.setScheduledAt(scheduledAt);
        }
        // Phân công lại việc đã bị từ chối: đưa về chờ tiếp nhận và xóa lý do từ chối cũ.
        if (status == TechnicalJobStatus.REJECTED) {
            job.setStatus(TechnicalJobStatus.ASSIGNED);
            job.setRejectionReason(null);
        }
        return toDetail(technicalJobRepository.save(job));
    }

    @Override
    @Transactional
    public TechnicalJobDetailResponse acceptJob(UUID technicalJobId) {
        TechnicalJob job = loadAndAuthorize(technicalJobId);
        transition(job, TechnicalJobStatus.IN_PROGRESS);
        return toDetail(technicalJobRepository.save(job));
    }

    @Override
    @Transactional
    public TechnicalJobDetailResponse rejectJob(UUID technicalJobId, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new AppException(ServicedeskErrorCode.REJECTION_REASON_REQUIRED);
        }
        TechnicalJob job = loadAndAuthorize(technicalJobId);
        transition(job, TechnicalJobStatus.REJECTED);
        job.setRejectionReason(reason.trim());
        return toDetail(technicalJobRepository.save(job));
    }

    @Override
    @Transactional
    public TechnicalJobDetailResponse completeJob(UUID technicalJobId, CompleteTechnicalJobRequest request) {
        if (request == null || !StringUtils.hasText(request.result())) {
            throw new AppException(ServicedeskErrorCode.JOB_RESULT_REQUIRED);
        }
        TechnicalJob job = loadAndAuthorize(technicalJobId);
        transition(job, TechnicalJobStatus.COMPLETED);
        job.setResult(request.result().trim());
        if (StringUtils.hasText(request.technicalNote())) {
            job.setTechnicalNote(request.technicalNote().trim());
        }
        job.setCompletedAt(OffsetDateTime.now());
        return toDetail(technicalJobRepository.save(job));
    }

    // ----- helpers -----

    /** Tải công việc và kiểm tra quyền thao tác (kỹ thuật viên chỉ được thao tác trên công việc của mình). */
    private TechnicalJob loadAndAuthorize(UUID technicalJobId) {
        TechnicalJob job = technicalJobRepository.findById(technicalJobId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.TECHNICAL_JOB_NOT_FOUND, technicalJobId));
        if (!isShopOwner() && !currentUserId().equals(job.getTechnicianId())) {
            throw new AppException(ServicedeskErrorCode.TECHNICAL_JOB_NOT_OWNED);
        }
        return job;
    }

    private void transition(TechnicalJob job, TechnicalJobStatus target) {
        TechnicalJobStatus current = job.getStatus();
        if (current == null || !current.canTransitionTo(target)) {
            throw new AppException(ServicedeskErrorCode.INVALID_JOB_STATUS_TRANSITION,
                    String.valueOf(current), target.name());
        }
        job.setStatus(target);
    }

    /** Xác thực người được phân công tồn tại và có vai trò TECHNICAL_STAFF. */
    private void requireTechnician(UUID technicianId) {
        UserSummaryDto user;
        try {
            user = iamApi.getUserById(technicianId);
        } catch (RuntimeException ex) {
            throw new AppException(ServicedeskErrorCode.INVALID_TECHNICIAN, String.valueOf(technicianId));
        }
        if (user == null || user.roles() == null || !user.roles().contains(TECHNICAL_STAFF_ROLE)) {
            throw new AppException(ServicedeskErrorCode.INVALID_TECHNICIAN, String.valueOf(technicianId));
        }
    }

    private boolean isShopOwner() {
        List<String> roles = currentUser().roles();
        return roles != null && roles.contains(SHOP_OWNER_ROLE);
    }

    private UserSummaryDto currentUser() {
        return iamApi.getCurrentUser();
    }

    private UUID currentUserId() {
        return iamApi.getCurrentUserId();
    }

    private TechnicalJobListItemResponse toListItem(TechnicalJob j) {
        return TechnicalJobListItemResponse.builder()
                .technicalJobId(j.getTechnicalJobId())
                .customerId(j.getCustomerId())
                .technicianId(j.getTechnicianId())
                .jobType(j.getJobType())
                .status(j.getStatus())
                .serviceAddress(j.getServiceAddress())
                .issueDescription(j.getIssueDescription())
                .scheduledAt(j.getScheduledAt())
                .completedAt(j.getCompletedAt())
                .createdAt(j.getCreatedAt())
                .build();
    }

    private TechnicalJobDetailResponse toDetail(TechnicalJob j) {
        return TechnicalJobDetailResponse.builder()
                .technicalJobId(j.getTechnicalJobId())
                .customerId(j.getCustomerId())
                .orderId(j.getOrderId())
                .warrantyRecordId(j.getWarrantyRecordId())
                .technicianId(j.getTechnicianId())
                .createdBy(j.getCreatedBy())
                .jobType(j.getJobType())
                .status(j.getStatus())
                .serviceAddress(j.getServiceAddress())
                .issueDescription(j.getIssueDescription())
                .result(j.getResult())
                .technicalNote(j.getTechnicalNote())
                .rejectionReason(j.getRejectionReason())
                .scheduledAt(j.getScheduledAt())
                .completedAt(j.getCompletedAt())
                .createdAt(j.getCreatedAt())
                .build();
    }
}
