package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.payload.ChangePasswordRequest;
import com.aquasuite.ims.module.iam.internal.payload.ForgotPasswordRequest;
import com.aquasuite.ims.module.iam.internal.payload.PasswordActionResponse;
import com.aquasuite.ims.module.iam.internal.payload.ResetPasswordWithOtpRequest;

public interface PasswordService {

    PasswordActionResponse changePassword(ChangePasswordRequest req);

    PasswordActionResponse requestPasswordRecovery(ForgotPasswordRequest req);

    PasswordActionResponse resetPasswordWithOtp(ResetPasswordWithOtpRequest req);
}
