package org.example.disciplinaryrecordsmanagement.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.AUTH_REQUIRED;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.CONFLICT;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.CSRF_INVALID;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.FORBIDDEN;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.INTERNAL_ERROR;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.INVALID_REQUEST;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.LOGIN_FAILED;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.METHOD_NOT_ALLOWED;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.NOT_ACCEPTABLE;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.NOT_FOUND;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.PASSWORD_CHANGE_REQUIRED;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.RATE_LIMITED;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.REGISTRATION_MAIL_UNAVAILABLE;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.SMTP_SETTINGS_LOCKED;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.UNSUPPORTED_MEDIA;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.UPLOAD_TOO_LARGE;

public enum ErrorCode {
    INVALID(4000, HttpStatus.BAD_REQUEST, INVALID_REQUEST),
    UNAUTHENTICATED(4010, HttpStatus.UNAUTHORIZED, AUTH_REQUIRED),
    BAD_CREDENTIALS(4011, HttpStatus.UNAUTHORIZED, LOGIN_FAILED),
    ACCESS_DENIED(4030, HttpStatus.FORBIDDEN, FORBIDDEN),
    INVALID_CSRF(4031, HttpStatus.FORBIDDEN, CSRF_INVALID),
    REQUIRE_PASSWORD_CHANGE(4032, HttpStatus.FORBIDDEN, PASSWORD_CHANGE_REQUIRED),
    SMTP_LOCKED(4033, HttpStatus.FORBIDDEN, SMTP_SETTINGS_LOCKED),
    MISSING(4040, HttpStatus.NOT_FOUND, NOT_FOUND),
    WRONG_METHOD(4050, HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED),
    UNACCEPTABLE(4060, HttpStatus.NOT_ACCEPTABLE, NOT_ACCEPTABLE),
    DUPLICATE(4090, HttpStatus.CONFLICT, CONFLICT),
    REQUEST_TOO_LARGE(4130, HttpStatus.valueOf(413), UPLOAD_TOO_LARGE),
    INVALID_MEDIA(4150, HttpStatus.UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_MEDIA),
    TOO_MANY_REQUESTS(4290, HttpStatus.TOO_MANY_REQUESTS, RATE_LIMITED),
    UNEXPECTED(5000, HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR),
    REGISTRATION_EMAIL_UNAVAILABLE(5030, HttpStatus.SERVICE_UNAVAILABLE, REGISTRATION_MAIL_UNAVAILABLE);

    private final int code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public int code() { return code; }
    public HttpStatus status() { return status; }
    public String message() { return message; }

    public static ErrorCode forHttpStatus(HttpStatusCode status) {
        if (status.is5xxServerError()) return UNEXPECTED;
        if (status.value() == REQUEST_TOO_LARGE.status().value()) return REQUEST_TOO_LARGE;
        return switch (HttpStatus.resolve(status.value())) {
            case UNAUTHORIZED -> UNAUTHENTICATED;
            case FORBIDDEN -> ACCESS_DENIED;
            case NOT_FOUND -> MISSING;
            case METHOD_NOT_ALLOWED -> WRONG_METHOD;
            case NOT_ACCEPTABLE -> UNACCEPTABLE;
            case CONFLICT -> DUPLICATE;
            case UNSUPPORTED_MEDIA_TYPE -> INVALID_MEDIA;
            case TOO_MANY_REQUESTS -> TOO_MANY_REQUESTS;
            case null, default -> INVALID;
        };
    }
}
