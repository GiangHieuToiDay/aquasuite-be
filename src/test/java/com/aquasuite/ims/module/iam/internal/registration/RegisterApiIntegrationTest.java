package com.aquasuite.ims.module.iam.internal.registration;

import com.aquasuite.ims.AquasuiteBeApplication;
import com.aquasuite.ims.module.iam.internal.service.AuthService;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.service.RegisterService;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.testsupport.AuthIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AquasuiteBeApplication.class)
@AutoConfigureMockMvc
abstract class RegisterTestSupport extends AuthIntegrationTestSupport {
    static final String ROOT = "/api/v1/iam/register";
    static final String EMAIL = "new@example.test";
    static final String PASSWORD = "SecurePass123!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @SpyBean UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @SpyBean StringRedisTemplate redis;
    @Autowired RegisterService registrations;
    @SpyBean AuthService auth;
    @MockBean JavaMailSender mail;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @SpyBean com.aquasuite.ims.module.crm.api.CustomerRegistrationApi customerProfiles;

    @BeforeEach
    void setupRegistration() {
        reset(users, auth, mail, redis);
        Object profileTarget =
                org.springframework.test.util.AopTestUtils.getUltimateTargetObject(
                        customerProfiles);
        reset(profileTarget);
        try {
            jdbc.execute("DELETE FROM public.customer_addresses");
            jdbc.execute("DELETE FROM public.customers");
        } catch (org.springframework.dao.DataAccessException ignored) {
        }
        users.deleteAll();
        Role customer = roles.findByRoleNameIgnoreCase("CUSTOMER").orElse(null);
        if (customer == null) {
            roles.save(
                    new Role(
                            UUID.fromString("10000000-0000-4000-8000-000000000006"),
                            "CUSTOMER",
                            "Customer",
                            true,
                            null,
                            Set.of()));
        } else if (!customer.isActive()) {
            customer.setActive(true);
            roles.save(customer);
        }
        for (String prefix : List.of(RegisterService.OTP_PREFIX + "*", "auth:*")) {
            var keys = redis.keys(prefix);
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        }
    }

    Map<String, Object> form() {
        var form = new LinkedHashMap<String, Object>();
        form.put("fullName", "Nguyễn Văn An");
        form.put("email", EMAIL);
        form.put("phone", "0901234567");
        form.put("password", PASSWORD);
        form.put("passwordConfirmation", PASSWORD);
        form.put("termsAccepted", true);
        return form;
    }

    MvcResult postJson(String path, Object body, int status) throws Exception {
        return mvc.perform(
                        post(path)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(body)))
                .andExpect(status().is(status))
                .andReturn();
    }

    User registered() throws Exception {
        postJson(ROOT, form(), 201);
        var state = redis.opsForHash().entries(
                com.aquasuite.ims.module.iam.internal.service.RegistrationOtpStore.key(EMAIL));
        User pending = new User();
        pending.setId(UUID.fromString(state.get("id").toString()));
        pending.setEmail(state.get("email").toString());
        pending.setPhone(state.get("phone").toString());
        pending.setFullName(state.get("fullName").toString());
        pending.setPasswordHash(state.get("passwordHash").toString());
        pending.setUsername("customer-" + pending.getId());
        return pending;
    }

    String otp() {
        var capture = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(capture.capture());
        var messages = capture.getAllValues();
        var matcher = Pattern.compile("code is: ([0-9]{6})").matcher(messages.getLast().getText());
        assertThat(matcher.find()).isTrue();
        assertThat(messages.getLast().getText()).contains("5 minutes");
        return matcher.group(1);
    }

    String key(User user) {
        return com.aquasuite.ims.module.iam.internal.service.RegistrationOtpStore.key(user.getEmail());
    }

    long redisNow() {
        return redis.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                "local t=redis.call('TIME'); return t[1]*1000+math.floor(t[2]/1000)",
                Long.class), List.of());
    }

    String verifyBody(String code) throws Exception {
        return json.writeValueAsString(Map.of("email", EMAIL, "otp", code));
    }
}

@org.junit.jupiter.api.extension.ExtendWith(
        org.springframework.boot.test.system.OutputCaptureExtension.class)
class RegisterApiIntegrationTest extends RegisterTestSupport {
    @Test
    void keepsPendingDataOnlyInRedisAndCreatesAccountAfterOtp() throws Exception {
        User user = registered();
        assertThat(users.count()).isZero();
        assertThat(user.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(encoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getUsername()).isEqualTo("customer-" + user.getId());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM customers WHERE user_id=?",
                                Integer.class,
                                user.getId()))
                .isZero();
        postJson("/api/v1/auth/login", Map.of("email", EMAIL, "password", PASSWORD), 401);
        String code = otp();
        assertThat(redis.opsForHash().get(key(user), "hash")).isNotEqualTo(code);
        assertThat(redis.getExpire(key(user))).isBetween(290L, 300L);
        var result = postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", code), 200);
        var body = json.readTree(result.getResponse().getContentAsString());
        String access = body.path("data").path("access_token").asText();
        assertThat(access).isNotBlank();
        assertThat(body.path("data").path("user").path("roles").toString()).contains("CUSTOMER");
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(users.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        var profile = jdbc.queryForMap("SELECT * FROM customers WHERE user_id=?", user.getId());
        assertThat(profile.get("customer_name")).isEqualTo(user.getFullName());
        assertThat(profile.get("email")).isEqualTo(user.getEmail());
        assertThat(profile.get("phone")).isEqualTo(user.getPhone());
        assertThat(profile.get("customer_type")).isEqualTo("RETAIL");
        assertThat(profile.get("status")).isEqualTo("ACTIVE");
        assertThat(profile.get("customer_code").toString()).startsWith("CUS-");
        assertThat(profile.get("address")).isNull();
        assertThat(profile.get("company_name")).isNull();
        assertThat(profile.get("created_at")).isNotNull();
        assertThat(profile.get("updated_at")).isNotNull();
        assertThat(redis.hasKey(key(user))).isFalse();
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", code), 409);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsDuplicateAccountsWithoutOverwritingCredentials() throws Exception {
        User user = registered();
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", otp()), 200);
        var duplicate = form();
        duplicate.put("email", EMAIL.toUpperCase(Locale.ROOT));
        duplicate.put("phone", "0912345678");
        postJson(ROOT, duplicate, 409);
        duplicate.put("email", "other@example.test");
        duplicate.put("phone", user.getPhone());
        postJson(ROOT, duplicate, 409);
        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findById(user.getId()).orElseThrow().getPasswordHash())
                .isEqualTo(user.getPasswordHash());
    }

    @Test
    void validatesWithoutPersistingAndDoesNotExposePassword(
            org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var invalid = form();
        invalid.put("password", "DontLogTheseCredentials987!");
        invalid.put("passwordConfirmation", "NeverLogThisConfirmation654!");
        invalid.put("termsAccepted", false);
        var response = postJson(ROOT, invalid, 400).getResponse().getContentAsString();
        assertThat(response)
                .contains("SYS_003", "passwordConfirmation", "termsAccepted")
                .doesNotContain(PASSWORD);
        assertThat(output.getAll())
                .doesNotContain("DontLogTheseCredentials987!", "NeverLogThisConfirmation654!");
        assertThat(users.count()).isZero();
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
    }

    @Test
    void preservesExactPublicAllowlistAndIgnoresPrivilegeInjection() throws Exception {
        for (String path : List.of(ROOT, ROOT + "/verifications", ROOT + "/otp-resends")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post(ROOT + "/arbitrary")).andExpect(status().isUnauthorized());
        var forged = form();
        forged.put("roles", List.of("ADMIN"));
        forged.put("status", "ACTIVE");
        postJson(ROOT, forged, 201);
        assertThat(users.count()).isZero();
        postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", otp()), 200);
        User user = users.findWithRolesAndPermissionsByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getRoles()).extracting(Role::getRoleName).containsExactly("CUSTOMER");
    }

    @Test
    void missingOrInactiveCustomerRoleFailsClosed() throws Exception {
        Role customer = roles.findByRoleNameIgnoreCase("CUSTOMER").orElseThrow();
        customer.setActive(false);
        roles.saveAndFlush(customer);
        try {
            registered();
            postJson(ROOT + "/verifications", Map.of("email", EMAIL, "otp", otp()), 503);
            assertThat(users.count()).isZero();
        } finally {
            customer.setActive(true);
            roles.saveAndFlush(customer);
        }
    }
}
