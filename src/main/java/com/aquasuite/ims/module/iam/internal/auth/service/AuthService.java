package com.aquasuite.ims.module.iam.internal.auth.service;

import com.aquasuite.ims.module.iam.internal.auth.dto.request.LoginRequest;
import com.aquasuite.ims.module.iam.internal.auth.dto.response.LoginResponse;
import com.aquasuite.ims.module.iam.internal.auth.dto.request.RefreshRequest;
import com.aquasuite.ims.module.iam.internal.auth.dto.response.TokenResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    TokenResponse refresh(RefreshRequest request);

    void logout(Jwt jwt);
}
