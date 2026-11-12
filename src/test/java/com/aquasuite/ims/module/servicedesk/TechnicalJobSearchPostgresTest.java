package com.aquasuite.ims.module.servicedesk;

import com.aquasuite.ims.module.servicedesk.internal.entity.TechnicalJob;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import com.aquasuite.ims.module.servicedesk.internal.repository.TechnicalJobRepository;
import com.aquasuite.ims.module.servicedesk.internal.repository.TechnicalJobSpecifications;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test chạy trên PostgreSQL thật (Testcontainers) cho truy vấn tra cứu công việc kỹ thuật (UC-59).
 * Kiểm chứng các bộ lọc tùy chọn (technicianId / status / jobType / khoảng scheduledAt) trong câu JPQL.
 * Yêu cầu Docker đang chạy (hoặc biến môi trường TEST_DATABASE_*).
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TechnicalJobSearchPostgresTest extends PostgreSqlIntegrationTestSupport {

    @Autowired private TechnicalJobRepository jobs;

    private final UUID techA = UUID.randomUUID();
    private final UUID techB = UUID.randomUUID();

    private UUID jobA1; // techA, INSTALLATION, ASSIGNED, scheduled day 10
    private UUID jobA2; // techA, WARRANTY_REPAIR, IN_PROGRESS, scheduled day 20
    private UUID jobB1; // techB, INSTALLATION, ASSIGNED, scheduled day 15

    private final OffsetDateTime day10 = OffsetDateTime.parse("2026-01-10T08:00:00Z");
    private final OffsetDateTime day15 = OffsetDateTime.parse("2026-01-15T08:00:00Z");
    private final OffsetDateTime day20 = OffsetDateTime.parse("2026-01-20T08:00:00Z");

    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeEach
    void seed() {
        jobs.deleteAll();
        jobA1 = save(techA, TechnicalJobType.INSTALLATION, TechnicalJobStatus.ASSIGNED, day10);
        jobA2 = save(techA, TechnicalJobType.WARRANTY_REPAIR, TechnicalJobStatus.IN_PROGRESS, day20);
        jobB1 = save(techB, TechnicalJobType.INSTALLATION, TechnicalJobStatus.ASSIGNED, day15);
    }

    private UUID save(UUID technicianId, TechnicalJobType type, TechnicalJobStatus status, OffsetDateTime scheduledAt) {
        TechnicalJob job = jobs.saveAndFlush(TechnicalJob.builder()
                .customerId(UUID.randomUUID())
                .technicianId(technicianId)
                .createdBy(UUID.randomUUID())
                .jobType(type)
                .status(status)
                .serviceAddress("123 Test street")
                .issueDescription("desc")
                .scheduledAt(scheduledAt)
                .build());
        return job.getTechnicalJobId();
    }

    private Set<UUID> ids(Page<TechnicalJob> page) {
        return page.getContent().stream().map(TechnicalJob::getTechnicalJobId).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Không lọc -> trả về toàn bộ công việc (góc nhìn Chủ cửa hàng)")
    void searchAll() {
        assertThat(ids(jobs.findAll(TechnicalJobSpecifications.filter(null, null, null, null, null), pageable)))
                .containsExactlyInAnyOrder(jobA1, jobA2, jobB1);
    }

    @Test
    @DisplayName("Lọc theo technicianId -> chỉ công việc của kỹ thuật viên đó")
    void filterByTechnician() {
        assertThat(ids(jobs.findAll(TechnicalJobSpecifications.filter(techA, null, null, null, null), pageable)))
                .containsExactlyInAnyOrder(jobA1, jobA2);
    }

    @Test
    @DisplayName("Lọc theo status và jobType")
    void filterByStatusAndType() {
        assertThat(ids(jobs.findAll(TechnicalJobSpecifications.filter(null, TechnicalJobStatus.ASSIGNED, null, null, null), pageable)))
                .containsExactlyInAnyOrder(jobA1, jobB1);
        assertThat(ids(jobs.findAll(TechnicalJobSpecifications.filter(null, null, TechnicalJobType.WARRANTY_REPAIR, null, null), pageable)))
                .containsExactly(jobA2);
    }

    @Test
    @DisplayName("Lọc theo khoảng lịch hẹn scheduledAt")
    void filterByScheduleRange() {
        assertThat(ids(jobs.findAll(TechnicalJobSpecifications.filter(null, null, null, day15, day20), pageable)))
                .containsExactlyInAnyOrder(jobB1, jobA2);
    }

    @Test
    @DisplayName("Lọc technicianId kết hợp khoảng lịch hẹn")
    void filterByTechnicianAndRange() {
        assertThat(ids(jobs.findAll(TechnicalJobSpecifications.filter(techA, null, null, null, day15), pageable)))
                .containsExactly(jobA1);
    }
}
