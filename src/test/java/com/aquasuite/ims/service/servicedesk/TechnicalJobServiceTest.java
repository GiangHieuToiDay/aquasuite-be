package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.dto.iam.response.UserSummaryDto;
import com.aquasuite.ims.entity.servicedesk.TechnicalJob;
import com.aquasuite.ims.enums.servicedesk.TechnicalJobStatus;
import com.aquasuite.ims.enums.servicedesk.TechnicalJobType;
import com.aquasuite.ims.entity.servicedesk.WarrantyTicket;
import com.aquasuite.ims.enums.servicedesk.WarrantyTicketStatus;
import com.aquasuite.ims.dto.servicedesk.request.CompleteTechnicalJobRequest;
import com.aquasuite.ims.repository.servicedesk.TechnicalJobRepository;
import com.aquasuite.ims.repository.servicedesk.WarrantyTicketRepository;
import com.aquasuite.ims.service.servicedesk.impl.TechnicalJobServiceImpl;
import com.aquasuite.ims.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit test cho nghiệp vụ công việc kỹ thuật: luật chuyển trạng thái (UC-60/61) và ràng buộc quyền sở hữu.
 */
@ExtendWith(MockitoExtension.class)
class TechnicalJobServiceTest {

    @Mock private TechnicalJobRepository repository;
    @Mock private WarrantyTicketRepository warrantyTicketRepository;
    @Mock private IamQueryService iamApi;
    @Mock private com.aquasuite.ims.service.crm.CustomerService crmApi;
    @InjectMocks private TechnicalJobServiceImpl service;

    private final UUID technicianId = UUID.randomUUID();
    private final UUID otherTechId = UUID.randomUUID();

    @BeforeEach
    void stubSaveEcho() {
        lenient().when(repository.save(any(TechnicalJob.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void loginAsTechnician() {
        when(iamApi.getCurrentUser()).thenReturn(userWithRoles("TECHNICAL_STAFF"));
        lenient().when(iamApi.getCurrentUserId()).thenReturn(technicianId);
    }

    private UserSummaryDto userWithRoles(String... roles) {
        return UserSummaryDto.builder().id(technicianId).roles(List.of(roles)).build();
    }

    private TechnicalJob job(TechnicalJobStatus status, UUID owner) {
        return TechnicalJob.builder()
                .technicalJobId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .technicianId(owner)
                .createdBy(UUID.randomUUID())
                .jobType(TechnicalJobType.INSTALLATION)
                .status(status)
                .serviceAddress("addr")
                .issueDescription("issue")
                .build();
    }

    private UserSummaryDto technicianUser(UUID id) {
        return UserSummaryDto.builder().id(id).roles(List.of("TECHNICAL_STAFF")).build();
    }

    @Test
    @DisplayName("createJob: khách hợp lệ, không gán KTV -> tạo job ASSIGNED, createdBy = người đăng nhập")
    void createJobSuccess() {
        UUID customerId = UUID.randomUUID();
        when(crmApi.getCustomerById(customerId))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(com.aquasuite.ims.dto.crm.response.CustomerSummaryDto.class)));
        when(iamApi.getCurrentUserId()).thenReturn(technicianId);

        var req = new com.aquasuite.ims.dto.servicedesk.request.CreateTechnicalJobRequest(
                TechnicalJobType.SURVEY, customerId, "12 Lê Lợi", "Khảo sát nhà tắm", null, null, null, null);
        var res = service.createJob(req);

        assertThat(res.status()).isEqualTo(TechnicalJobStatus.ASSIGNED);
        assertThat(res.jobType()).isEqualTo(TechnicalJobType.SURVEY);
        assertThat(res.createdBy()).isEqualTo(technicianId);
        assertThat(res.technicianId()).isNull();
    }

    @Test
    @DisplayName("createJob: khách không tồn tại -> lỗi CUSTOMER_NOT_FOUND")
    void createJobCustomerNotFound() {
        UUID customerId = UUID.randomUUID();
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.empty());

        var req = new com.aquasuite.ims.dto.servicedesk.request.CreateTechnicalJobRequest(
                TechnicalJobType.INSTALLATION, customerId, "addr", "desc", null, null, null, null);
        assertThatThrownBy(() -> service.createJob(req)).isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("assign: gán kỹ thuật viên hợp lệ cho việc ASSIGNED")
    void assignSetsTechnician() {
        TechnicalJob j = job(TechnicalJobStatus.ASSIGNED, null);
        when(repository.findById(j.getTechnicalJobId())).thenReturn(Optional.of(j));
        when(iamApi.getUserById(otherTechId)).thenReturn(technicianUser(otherTechId));

        var res = service.assignJob(j.getTechnicalJobId(), otherTechId, null);

        assertThat(res.technicianId()).isEqualTo(otherTechId);
        assertThat(res.status()).isEqualTo(TechnicalJobStatus.ASSIGNED);
    }

    @Test
    @DisplayName("assign kèm lịch hẹn -> đặt scheduledAt cho công việc")
    void assignSetsSchedule() {
        TechnicalJob j = job(TechnicalJobStatus.ASSIGNED, null);
        when(repository.findById(j.getTechnicalJobId())).thenReturn(Optional.of(j));
        when(iamApi.getUserById(otherTechId)).thenReturn(technicianUser(otherTechId));
        java.time.OffsetDateTime when = java.time.OffsetDateTime.parse("2026-02-01T09:00:00Z");

        var res = service.assignJob(j.getTechnicalJobId(), otherTechId, when);

        assertThat(res.technicianId()).isEqualTo(otherTechId);
        assertThat(res.scheduledAt()).isEqualTo(when);
    }

    @Test
    @DisplayName("assign lại việc REJECTED -> về ASSIGNED, xóa lý do từ chối")
    void reassignRejectedResetsToAssigned() {
        TechnicalJob j = job(TechnicalJobStatus.REJECTED, technicianId);
        j.setRejectionReason("bận");
        when(repository.findById(j.getTechnicalJobId())).thenReturn(Optional.of(j));
        when(iamApi.getUserById(otherTechId)).thenReturn(technicianUser(otherTechId));

        var res = service.assignJob(j.getTechnicalJobId(), otherTechId, null);

        assertThat(res.status()).isEqualTo(TechnicalJobStatus.ASSIGNED);
        assertThat(res.technicianId()).isEqualTo(otherTechId);
        assertThat(res.rejectionReason()).isNull();
    }

    @Test
    @DisplayName("assign người không phải kỹ thuật viên -> lỗi INVALID_TECHNICIAN")
    void assignInvalidTechnician() {
        TechnicalJob j = job(TechnicalJobStatus.ASSIGNED, null);
        when(repository.findById(j.getTechnicalJobId())).thenReturn(Optional.of(j));
        when(iamApi.getUserById(otherTechId))
                .thenReturn(UserSummaryDto.builder().id(otherTechId).roles(List.of("SALES_STAFF")).build());

        assertThatThrownBy(() -> service.assignJob(j.getTechnicalJobId(), otherTechId, null))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("assign việc đã IN_PROGRESS -> lỗi JOB_NOT_ASSIGNABLE")
    void assignNotAssignable() {
        TechnicalJob j = job(TechnicalJobStatus.IN_PROGRESS, technicianId);
        when(repository.findById(j.getTechnicalJobId())).thenReturn(Optional.of(j));

        assertThatThrownBy(() -> service.assignJob(j.getTechnicalJobId(), otherTechId, null))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("accept: ASSIGNED -> IN_PROGRESS")
    void acceptMovesToInProgress() {
        loginAsTechnician();
        TechnicalJob j = job(TechnicalJobStatus.ASSIGNED, technicianId);
        when(repository.findById(j.getTechnicalJobId())).thenReturn(Optional.of(j));

        var res = service.acceptJob(j.getTechnicalJobId());

        assertThat(res.status()).isEqualTo(TechnicalJobStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("accept khi đã IN_PROGRESS -> lỗi chuyển trạng thái")
    void acceptInvalidTransition() {
        loginAsTechnician();
        TechnicalJob j = job(TechnicalJobStatus.IN_PROGRESS, technicianId);
        when(repository.findById(j.getTechnicalJobId())).thenReturn(Optional.of(j));

        assertThatThrownBy(() -> service.acceptJob(j.getTechnicalJobId()))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("complete: IN_PROGRESS -> COMPLETED, set kết quả và completedAt")
    void completeMovesToCompleted() {
        loginAsTechnician();
        TechnicalJob j = job(TechnicalJobStatus.IN_PROGRESS, technicianId);
        when(repository.findById(j.getTechnicalJobId())).thenReturn(Optional.of(j));

        var res = service.completeJob(j.getTechnicalJobId(), new CompleteTechnicalJobRequest("OK result", "note"));

        assertThat(res.status()).isEqualTo(TechnicalJobStatus.COMPLETED);
        assertThat(res.result()).isEqualTo("OK result");
        assertThat(res.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("Kỹ thuật viên thao tác công việc không thuộc mình -> lỗi NOT_OWNED")
    void cannotActOnOthersJob() {
        loginAsTechnician();
        TechnicalJob j = job(TechnicalJobStatus.ASSIGNED, otherTechId);
        when(repository.findById(j.getTechnicalJobId())).thenReturn(Optional.of(j));

        assertThatThrownBy(() -> service.acceptJob(j.getTechnicalJobId()))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("complete thiếu kết quả -> lỗi JOB_RESULT_REQUIRED")
    void completeRequiresResult() {
        // Không cần login: validate input xảy ra trước khi truy cập dữ liệu.
        assertThatThrownBy(() -> service.completeJob(UUID.randomUUID(), new CompleteTechnicalJobRequest("  ", null)))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("dispatchWarrantyRepairJob: tạo job WARRANTY_REPAIR ASSIGNED gắn ticket + record, gán KTV")
    void dispatchWarrantyRepairJobSuccess() {
        UUID customerId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(repository.existsByWarrantyTicketId(ticketId)).thenReturn(false);
        when(iamApi.getUserById(technicianId)).thenReturn(technicianUser(technicianId));
        when(iamApi.getCurrentUserId()).thenReturn(otherTechId);

        var res = service.dispatchWarrantyRepairJob(customerId, recordId, ticketId, technicianId,
                "123 Trần Phú", "Máy nóng lạnh rò nước", null);

        assertThat(res.jobType()).isEqualTo(TechnicalJobType.WARRANTY_REPAIR);
        assertThat(res.status()).isEqualTo(TechnicalJobStatus.ASSIGNED);
        assertThat(res.warrantyTicketId()).isEqualTo(ticketId);
        assertThat(res.warrantyRecordId()).isEqualTo(recordId);
        assertThat(res.technicianId()).isEqualTo(technicianId);
        assertThat(res.createdBy()).isEqualTo(otherTechId);
    }

    @Test
    @DisplayName("dispatchWarrantyRepairJob: ticket đã có job -> lỗi (idempotent)")
    void dispatchWarrantyRepairJobIdempotent() {
        UUID ticketId = UUID.randomUUID();
        when(repository.existsByWarrantyTicketId(ticketId)).thenReturn(true);

        assertThatThrownBy(() -> service.dispatchWarrantyRepairJob(
                UUID.randomUUID(), UUID.randomUUID(), ticketId, technicianId, "addr", "issue", null))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("complete job WARRANTY_REPAIR có ticket -> đẩy ticket IN_PROGRESS sang DONE, copy result")
    void completeWarrantyRepairFeedsTicketDone() {
        loginAsTechnician();
        UUID ticketId = UUID.randomUUID();
        TechnicalJob j = job(TechnicalJobStatus.IN_PROGRESS, technicianId);
        j.setJobType(TechnicalJobType.WARRANTY_REPAIR);
        j.setWarrantyTicketId(ticketId);
        when(repository.findById(j.getTechnicalJobId())).thenReturn(Optional.of(j));

        WarrantyTicket ticket = WarrantyTicket.builder()
                .ticketId(ticketId)
                .ticketCode("TCK-WAR-202611-0001")
                .warrantyRecordId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .issueDescription("issue")
                .siteAddress("addr")
                .contactName("A").contactPhone("0900").status(WarrantyTicketStatus.IN_PROGRESS)
                .technicalJobId(j.getTechnicalJobId())
                .build();
        when(warrantyTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        service.completeJob(j.getTechnicalJobId(), new CompleteTechnicalJobRequest("Đã sửa theo bảo hành", null));

        assertThat(ticket.getStatus()).isEqualTo(WarrantyTicketStatus.DONE);
        assertThat(ticket.getResult()).isEqualTo("Đã sửa theo bảo hành");
        assertThat(ticket.getCompletedAt()).isNotNull();
        org.mockito.Mockito.verify(warrantyTicketRepository).save(ticket);
    }
}
