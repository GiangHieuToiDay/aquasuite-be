package com.aquasuite.ims.module.iam.internal.entity;


import com.aquasuite.ims.module.iam.internal.entity.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "email", unique = true, length = 150)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "password_last_changed_at")
    private Instant passwordLastChangedAt;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "provider_id")
    private String providerId;

//    @Enumerated(EnumType.STRING)
//    @Column(name = "provider", length = 50)
//    private AuthProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private UserStatus status;

    @Column(name = "verification_code", length = 100)
    private String verificationCode;

    @Column(name = "fcm_token", columnDefinition = "TEXT")
    private String fcmToken;

    @Column(name = "verification_expired_at")
    private Instant verificationExpiredAt;

    @Column(name = "is_verified")
    private Boolean isVerified;

    @Column(name = "email_sent_count")
    private Integer emailSentCount;

    @Column(name = "email_sent_date")
    private LocalDate emailSentDate;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "permission_id")
    private Long permissionId;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (isVerified == null) {
            isVerified = false;
        }
        if (emailSentCount == null) {
            emailSentCount = 0;
        }
    }
}
