package com.aquasuite.ims.module.iam.internal.legacy.service;

import com.aquasuite.ims.module.iam.internal.legacy.payload.ChangePasswordRequest;
import com.aquasuite.ims.module.iam.internal.legacy.payload.ForgotPasswordRequest;
import com.aquasuite.ims.module.iam.internal.legacy.payload.PasswordActionResponse;
import com.aquasuite.ims.module.iam.internal.legacy.payload.ResetPasswordWithOtpRequest;

public interface PasswordService {

    PasswordActionResponse changePassword(ChangePasswordRequest req);

    PasswordActionResponse requestPasswordRecovery(ForgotPasswordRequest req);

    PasswordActionResponse resetPasswordWithOtp(ResetPasswordWithOtpRequest req);
}
