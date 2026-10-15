package com.aquasuite.ims.share.exception;

import org.springframework.http.HttpStatusCode;

public interface BaseErrorCode {
    String getCode();
    String getMessage();
    HttpStatusCode getStatus();
}
