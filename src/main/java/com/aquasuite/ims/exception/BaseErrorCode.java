package com.aquasuite.ims.exception;

import org.springframework.http.HttpStatusCode;

public interface BaseErrorCode {
    String getCode();

    String getMessage();

    HttpStatusCode getStatus();
}
