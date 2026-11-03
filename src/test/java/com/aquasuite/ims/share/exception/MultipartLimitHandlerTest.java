package com.aquasuite.ims.share.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultipartLimitHandlerTest {
    @Test
    void commonMultipartLimitReturnsNeutral413() {
        var response = new GlobalExceptionHandler().handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1));
        assertEquals(413, response.getStatusCode().value());
        assertEquals("SYS_005", response.getBody().getCode());
    }
}
