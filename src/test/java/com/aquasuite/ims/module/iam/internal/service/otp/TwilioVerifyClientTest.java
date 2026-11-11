package com.aquasuite.ims.module.iam.internal.service.otp;

import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TwilioVerifyClientTest {
    private static final String SERVICE = "VA" + "1".repeat(32);
    private static final String SID = "VE" + "2".repeat(32);
    private MockRestServiceServer server;
    private TwilioVerifyClient client;

    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("https://verify.twilio.com/v2");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TwilioVerifyClient(builder.build(), SERVICE);
    }

    @Test void sendsSmsToVietnamE164Phone() {
        server.expect(requestTo("https://verify.twilio.com/v2/Services/" + SERVICE + "/Verifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("To=%2B84901234567&Channel=sms"))
                .andRespond(withSuccess("{\"sid\":\"" + SID + "\",\"status\":\"pending\"}", MediaType.APPLICATION_JSON));
        assertThat(client.send("0901234567")).isEqualTo(SID);
        server.verify();
    }

    @Test void checksSpecificVerificationAndPreservesLeadingZero() {
        server.expect(requestTo("https://verify.twilio.com/v2/Services/" + SERVICE + "/VerificationCheck"))
                .andExpect(content().string("VerificationSid=" + SID + "&Code=012345"))
                .andRespond(withSuccess("{\"sid\":\"" + SID + "\",\"status\":\"approved\"}", MediaType.APPLICATION_JSON));
        assertThat(client.check(SID, "012345")).isTrue();
        server.verify();
    }

    @Test void unapprovedCodeCannotActivateAccount() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"sid\":\"" + SID + "\",\"status\":\"pending\"}", MediaType.APPLICATION_JSON));
        assertThat(client.check(SID, "000000")).isFalse();
    }

    @Test void expiredProviderVerificationIsInvalid() {
        server.expect(anything()).andRespond(withResourceNotFound());
        assertThat(client.check(SID, "012345")).isFalse();
    }

    @Test void approvalForAnotherVerificationIsRejected() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"sid\":\"VE" + "3".repeat(32) + "\",\"status\":\"approved\"}", MediaType.APPLICATION_JSON));
        assertThat(client.check(SID, "012345")).isFalse();
    }

    @Test void providerOutageDoesNotLeakResponse() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("synthetic-sensitive-provider-response"));
        assertThatThrownBy(() -> client.send("0901234567"))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getErrorCode().getCode()).isEqualTo("REG_010");
                    assertThat(ex.getMessage()).doesNotContain("synthetic-sensitive");
                    assertThat(ex.getCause()).isNull();
                });
    }

    @Test void providerRateLimitMapsTo429() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.send("0901234567"))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo("REG_004"));
    }

    @Test void missingConfigurationFailsClosed() {
        var unconfigured = new TwilioVerifyClient(RestClient.builder(), "", "", "");
        assertThatThrownBy(() -> unconfigured.send("0901234567")).isInstanceOf(AppException.class);
    }
}
