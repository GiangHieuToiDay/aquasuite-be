package com.aquasuite.ims.module.servicedesk.internal.dev;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.servicedesk.internal.entity.TechnicalJob;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import com.aquasuite.ims.module.servicedesk.internal.repository.TechnicalJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seed dữ liệu công việc kỹ thuật mẫu cho môi trường phát triển (FE-13).
 * <p>Chỉ chạy khi profile <b>dev</b> đang bật (và không phải prod), và chỉ khi bảng còn trống — để
 * đội FE/BE có sẵn dữ liệu bấm thử luồng UC-59/60/61 và thao tác phân công mà không cần dựng đơn hàng thật.
 * Không bao giờ chạy ở môi trường thật; đây không phải nguồn dữ liệu nghiệp vụ chính thức.</p>
 *
 * <p>Tạo 3 công việc minh hoạ:</p>
 * <ol>
 *   <li>INSTALLATION / ASSIGNED, <i>chưa phân công</i> — để Chủ cửa hàng thử nút Phân công.</li>
 *   <li>SURVEY / ASSIGNED, đã gán cho kỹ thuật viên dev — để thử Tiếp nhận / Từ chối.</li>
 *   <li>INSTALLATION / IN_PROGRESS, đã gán cho kỹ thuật viên dev — để thử Hoàn tất.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TechnicalJobDevDataInitializer implements ApplicationRunner {

    private static final String TECHNICIAN_EMAIL = "technical@aquasuite.local";
    private static final String OWNER_EMAIL = "owner@aquasuite.local";
    private static final String CUSTOMER_EMAIL = "customer@aquasuite.com";

    private final TechnicalJobRepository technicalJobRepository;
    private final IamApi iamApi;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        Set<String> profiles = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase).collect(Collectors.toSet());
        if (profiles.contains("prod") || !profiles.contains("dev")) {
            return; // Chỉ seed ở môi trường dev.
        }
        if (technicalJobRepository.count() > 0) {
            return; // Đã có dữ liệu — không ghi đè.
        }

        UUID technicianId = resolve(TECHNICIAN_EMAIL);
        UUID ownerId = resolve(OWNER_EMAIL);
        UUID customerId = resolve(CUSTOMER_EMAIL);

        // createdBy bắt buộc non-null: ưu tiên owner, lùi về technician nếu thiếu.
        UUID createdBy = ownerId != null ? ownerId : technicianId;
        if (createdBy == null || customerId == null) {
            log.warn("Bỏ qua seed công việc kỹ thuật: thiếu tài khoản dev (owner/customer). "
                    + "Hãy đảm bảo đã seed owner@ và customer@.");
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        TechnicalJob unassigned = TechnicalJob.builder()
                .customerId(customerId)
                .createdBy(createdBy)
                .jobType(TechnicalJobType.INSTALLATION)
                .status(TechnicalJobStatus.ASSIGNED)
                .serviceAddress("123 Lê Lợi, Quận 1, TP.HCM")
                .issueDescription("Lắp đặt bồn cầu và lavabo cho căn hộ mẫu (job seed — chưa phân công)")
                .scheduledAt(now.plusDays(1))
                .build();

        TechnicalJob assignedToTech = TechnicalJob.builder()
                .customerId(customerId)
                .technicianId(technicianId)
                .createdBy(createdBy)
                .jobType(TechnicalJobType.SURVEY)
                .status(TechnicalJobStatus.ASSIGNED)
                .serviceAddress("45 Nguyễn Huệ, Quận 1, TP.HCM")
                .issueDescription("Khảo sát hiện trạng nhà tắm trước khi báo giá (job seed — chờ kỹ thuật viên tiếp nhận)")
                .scheduledAt(now.plusDays(2))
                .build();

        TechnicalJob inProgress = TechnicalJob.builder()
                .customerId(customerId)
                .technicianId(technicianId)
                .createdBy(createdBy)
                .jobType(TechnicalJobType.INSTALLATION)
                .status(TechnicalJobStatus.IN_PROGRESS)
                .serviceAddress("78 Điện Biên Phủ, Quận 3, TP.HCM")
                .issueDescription("Lắp đặt hệ sen tắm và vòi rửa (job seed — đang thực hiện, chờ hoàn tất)")
                .scheduledAt(now)
                .build();

        technicalJobRepository.saveAll(Arrays.asList(unassigned, assignedToTech, inProgress));
        log.info("Seeded 3 technical jobs for dev (technicianId={} resolved from {}).",
                technicianId, TECHNICIAN_EMAIL);
        if (technicianId == null) {
            log.warn("Không tìm thấy tài khoản kỹ thuật viên {} — 2 job gán-sẵn sẽ không có người phụ trách. "
                    + "Tạo tài khoản role TECHNICAL_STAFF với email đó rồi xoá dữ liệu seed để chạy lại.", TECHNICIAN_EMAIL);
        }
    }

    private UUID resolve(String email) {
        Optional<UserSummaryDto> user = iamApi.findByEmail(email);
        return user.map(UserSummaryDto::id).orElse(null);
    }
}
