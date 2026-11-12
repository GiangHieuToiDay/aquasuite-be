package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.crm.api.CustomerRegistrationApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerRegistrationRequest;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.error.RegistrationErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.request.RegisterRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.ResendRegistrationOtpRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.VerifyRegistrationRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.LoginResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.RegisterResponse;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import java.util.UUID;

/**
 * Điều phối đăng ký khách hàng bằng email OTP: lưu form chờ trong Redis, xác thực email,
 * rồi tạo tài khoản và hồ sơ CRM trong cùng transaction trước khi đăng nhập.
 */
@Service
@Validated
@RequiredArgsConstructor
public class RegisterService {
    public static final String OTP_PREFIX = RegistrationOtpStore.PREFIX;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final RegistrationOtpStore otpStore;
    private final TransactionTemplate transactions;
    private final AuthService auth;
    private final CustomerRegistrationApi customerProfiles;

    /** Kiểm tra trùng email/SĐT, hash mật khẩu và lưu phiên chờ kèm yêu cầu gửi email OTP. */
    public RegisterResponse register(@Valid RegisterRequest request) {
        PendingRegistration pending;
        try {
            checkDuplicates(request.email(), request.phone());
            pending = new PendingRegistration(UUID.randomUUID(), request.fullName(),
                    request.email(), request.phone(), encoder.encode(request.password()));
            otpStore.save(pending);
        } catch (DataAccessException ex) {
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
        return new RegisterResponse(pending.id(), pending.email(), "PENDING_ACTIVATION");
    }

    /** Gửi lại OTP cho phiên còn chờ; từ chối khi email đã có tài khoản hoặc phiên bị giới hạn. */
    public void resend(@Valid ResendRegistrationOtpRequest request) {
        try {
            if (users.existsByEmailIgnoreCase(request.email())) {
                throw new AppException(RegistrationErrorCode.NOT_PENDING);
            }
            otpStore.rotateOtp(request.email());
        } catch (DataAccessException ex) {
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
    }

    /**
     * Xác thực và giữ quyền xử lý phiên OTP trước khi tạo tài khoản/hồ sơ CRM atomically.
     * Rollback sẽ nhả quyền xử lý để thử lại; lỗi đăng nhập sau commit không xóa tài khoản đã tạo.
     */
    public LoginResponse verify(@Valid VerifyRegistrationRequest request) {
        RegistrationOtpStore.Claim claim;
        try {
            if (users.existsByEmailIgnoreCase(request.email())) {
                throw new AppException(RegistrationErrorCode.NOT_PENDING);
            }
            claim = otpStore.verifyAndClaim(request.email(), request.otp());
        } catch (DataAccessException ex) {
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
        UUID userId;
        try {
            userId = transactions.execute(tx -> createAccount(claim.registration()));
        } catch (RuntimeException ex) {
            release(claim);
            if (ex instanceof AppException app) throw app;
            if (ex instanceof DataIntegrityViolationException) {
                throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
            }
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
        try {
            otpStore.complete(claim);
        } catch (RuntimeException ignored) {
            // Account is committed; database duplicate checks prevent replay until Redis expires.
        }
        try {
            return auth.loginRegisteredUser(userId);
        } catch (RuntimeException ex) {
            throw new AppException(RegistrationErrorCode.LOGIN_REQUIRED);
        }
    }

    /** Tạo tài khoản ACTIVE với role CUSTOMER đang hoạt động và hồ sơ CRM trong transaction của caller. */
    private UUID createAccount(PendingRegistration pending) {
        checkDuplicates(pending.email(), pending.phone());
        var role = roles.findByRoleNameIgnoreCase("CUSTOMER")
                .filter(r -> r.isActive())
                .orElseThrow(() -> new AppException(RegistrationErrorCode.UNAVAILABLE));
        // Reuse the conflict-safe insert without changing the shared repository.
        // PENDING is changed to ACTIVE before this transaction commits.
        int inserted = users.insertPendingRegistration(pending.id(), "customer-" + pending.id(),
                pending.fullName(), pending.email(), pending.phone(), pending.passwordHash());
        if (inserted != 1) {
            checkDuplicates(pending.email(), pending.phone());
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        var user = users.findById(pending.id())
                .orElseThrow(() -> new AppException(RegistrationErrorCode.UNAVAILABLE));
        user.getRoles().add(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setVerified(true);
        users.saveAndFlush(user);
        try {
            customerProfiles.createProfile(new CustomerRegistrationRequest(user.getId(),
                    user.getFullName(), user.getPhone(), user.getEmail()));
        } catch (AppException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AppException(RegistrationErrorCode.UNAVAILABLE);
        }
        return user.getId();
    }

    /** Từ chối nếu email hoặc SĐT đã thuộc tài khoản hiện có, kể cả khi có đăng ký đồng thời. */
    private void checkDuplicates(String email, String phone) {
        if (users.existsByEmailIgnoreCase(email) || users.existsByPhone(phone)) {
            throw new AppException(RegistrationErrorCode.DUPLICATE_ACCOUNT);
        }
    }

    /** Nhả claim sau rollback; nếu Redis lỗi thì giữ khóa đến khi phiên tự hết hạn để tránh xử lý trùng. */
    private void release(RegistrationOtpStore.Claim claim) {
        try {
            otpStore.release(claim);
        } catch (RuntimeException ignored) {
            // Failed dependency may keep the claim until the existing five-minute TTL expires.
        }
    }

}
