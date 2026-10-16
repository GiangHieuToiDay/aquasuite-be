package com.aquasuite.ims.module.iam.internal.config;

import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.enums.UserStatus;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class IamDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String testEmail = "ganrt.hieu.08@gmail.com";

        if (userRepository.findByEmailIgnoreCase(testEmail).isEmpty()) {
            User testUser = User.builder()
                    .username("hieuganrt")
                    .email(testEmail)
                    .password(passwordEncoder.encode("Password123@"))
                    .phone("0987654321")
                    .status(UserStatus.ACTIVE)
                    .isVerified(true)
                    .build();

            userRepository.save(testUser);
            log.info("Initialized test user: email = {}, password = Password123@", testEmail);
        } else {
            log.info("Test user with email {} already exists, skip init.", testEmail);
        }
    }
}
