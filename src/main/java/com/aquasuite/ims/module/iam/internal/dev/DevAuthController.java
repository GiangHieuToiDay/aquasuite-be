package com.aquasuite.ims.module.iam.internal.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev")
public class DevAuthController {

    @GetMapping("/api/v1/dev/auth-check")
    public String check(Authentication authentication) {
        return "Authenticated: " + authentication.getName();
    }
}
