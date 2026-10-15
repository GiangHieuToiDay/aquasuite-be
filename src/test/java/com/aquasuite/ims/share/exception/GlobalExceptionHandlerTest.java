package com.aquasuite.ims.share.exception;

import com.aquasuite.ims.share.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private GlobalExceptionHandler exceptionHandler;

    private enum TestErrorCode implements BaseErrorCode {
        INSUFFICIENT_STOCK("INV_001", "SKU {0} không đủ tồn kho (Yêu cầu: {1}, Khả dụng: {2})", HttpStatus.CONFLICT);

        private final String code;
        private final String message;
        private final HttpStatus status;

        TestErrorCode(String code, String message, HttpStatus status) {
            this.code = code;
            this.message = message;
            this.status = status;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public HttpStatusCode getStatus() {
            return status;
        }
    }

    @RestController
    static class DummyController {
        @GetMapping("/test/app-exception")
        public void throwAppException() {
            throw new AppException(TestErrorCode.INSUFFICIENT_STOCK, "SKU-TEST-01", 10, 3);
        }

        @GetMapping("/test/access-denied")
        public void throwAccessDenied() {
            throw new AccessDeniedException("Access denied test");
        }

        @GetMapping("/test/fallback")
        public void throwFallback() {
            throw new RuntimeException("Unexpected runtime error");
        }
    }

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    @DisplayName("Throw AppException with placeholders formats message and returns correct HTTP status and ApiResponse")
    void testHandleAppExceptionWithPlaceholders_DirectMethod() {
        AppException exception = new AppException(TestErrorCode.INSUFFICIENT_STOCK, "SKU-TEST-01", 10, 3);

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAppException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo("INV_001");
        assertThat(response.getBody().getMessage()).isEqualTo("SKU SKU-TEST-01 không đủ tồn kho (Yêu cầu: 10, Khả dụng: 3)");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getErrors()).isNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("MockMvc test for AppException: returns HTTP 409 Conflict with standard ApiResponse JSON body")
    void testHandleAppException_ViaMockMvc() throws Exception {
        mockMvc.perform(get("/test/app-exception")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INV_001"))
                .andExpect(jsonPath("$.message").value("SKU SKU-TEST-01 không đủ tồn kho (Yêu cầu: 10, Khả dụng: 3)"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("MockMvc test for AccessDeniedException: returns HTTP 403 Forbidden with AUTH_002 code")
    void testHandleAccessDeniedException_ViaMockMvc() throws Exception {
        mockMvc.perform(get("/test/access-denied")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_002"))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.ACCESS_DENIED.getMessage()))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("MockMvc test for unhandled Exception: returns HTTP 500 without leaking raw internal error")
    void testHandleFallbackException_ViaMockMvc() throws Exception {
        mockMvc.perform(get("/test/fallback")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SYS_001"))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
