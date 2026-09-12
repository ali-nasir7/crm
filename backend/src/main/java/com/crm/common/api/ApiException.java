package com.crm.common.api;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final ErrorCode code;
    private final transient Object details;

    private ApiException(HttpStatus status, ErrorCode code, String message, Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public static ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message, null); }
    public static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message, null); }
    public static ApiException badRequest(String message, Object details) { return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message, details); }
    public static ApiException forbidden(String message) { return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, message, null); }
    public static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message, null); }
    public static ApiException business(String message) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.BUSINESS_RULE, message, null); }
    public static ApiException rateLimited(String message) { return new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, message, null); }
    public static ApiException unauthorized(String message) { return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, message, null); }
    public static ApiException passwordChangeRequired(String message) { return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.PASSWORD_CHANGE_REQUIRED, message, null); }
}
