package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.payload.request.LoginRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.RefreshRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.LoginResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.TokenResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    /** Internal IAM call after registration activation commits. */
    LoginResponse loginRegisteredUser(java.util.UUID userId);

    TokenResponse refresh(RefreshRequest request);

    void logout(Jwt jwt);
}
