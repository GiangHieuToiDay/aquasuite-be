package com.aquasuite.ims.module.iam.internal.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.UUID;

/** Recovery state kept separate from the authentication User schema. OTP is stored only as a hash. */
@Entity @Table(name = "user_password_recovery")
@Getter @Setter @NoArgsConstructor
public class UserPasswordRecovery {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "verification_code", length = 255) private String verificationCode;
    @Column(name = "verification_expired_at") private LocalDateTime verificationExpiredAt;
    @Column(name = "email_sent_date") private LocalDate emailSentDate;
    @Column(name = "email_sent_count") private Integer emailSentCount;
    @Column(name = "password_last_changed_at") private LocalDateTime passwordLastChangedAt;
}
