package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.payload.request.ChangePasswordRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.ForgotPasswordRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.ResetPasswordWithOtpRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.PasswordActionResponse;

public interface PasswordService {

    PasswordActionResponse changePassword(ChangePasswordRequest req);

    PasswordActionResponse requestPasswordRecovery(ForgotPasswordRequest req);

    PasswordActionResponse resetPasswordWithOtp(ResetPasswordWithOtpRequest req);
}
