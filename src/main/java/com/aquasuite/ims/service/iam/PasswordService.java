package com.aquasuite.ims.service.iam;

import com.aquasuite.ims.dto.iam.request.ChangePasswordRequest;
import com.aquasuite.ims.dto.iam.request.ForgotPasswordRequest;
import com.aquasuite.ims.dto.iam.request.ResetPasswordWithOtpRequest;
import com.aquasuite.ims.dto.iam.response.PasswordActionResponse;

public interface PasswordService {

    PasswordActionResponse changePassword(ChangePasswordRequest req);

    PasswordActionResponse requestPasswordRecovery(ForgotPasswordRequest req);

    PasswordActionResponse resetPasswordWithOtp(ResetPasswordWithOtpRequest req);
}
