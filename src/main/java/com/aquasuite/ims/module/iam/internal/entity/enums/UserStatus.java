package com.aquasuite.ims.module.iam.internal.entity.enums;

public enum UserStatus {
    /** Tài khoản mới đăng ký, chưa kích hoạt qua mã xác thực email/SĐT */
    PENDING,

    /** Tài khoản đang hoạt động bình thường */
    ACTIVE,

    /** Tài khoản bị khóa (do vi phạm, nhập sai mật khẩu nhiều lần, v.v.) */
    INACTIVE,

    /** Tài khoản đã bị xóa mềm (Soft Delete) */
    DELETED
}