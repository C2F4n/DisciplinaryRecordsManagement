package org.example.disciplinaryrecordsmanagement.exception;

public final class BusinessException extends RuntimeException {
    private final ErrorCode code;

    private BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() { return code; }

    public static BusinessException bad(String message) { return new BusinessException(ErrorCode.INVALID, message); }
    public static BusinessException unauthorized() { return of(ErrorCode.UNAUTHENTICATED); }
    public static BusinessException forbidden() { return of(ErrorCode.ACCESS_DENIED); }
    public static BusinessException passwordChangeRequired() { return of(ErrorCode.REQUIRE_PASSWORD_CHANGE); }
    public static BusinessException smtpLocked() { return of(ErrorCode.SMTP_LOCKED); }
    public static BusinessException registrationEmailUnavailable() { return of(ErrorCode.REGISTRATION_EMAIL_UNAVAILABLE); }
    public static BusinessException rateLimited(String message) { return new BusinessException(ErrorCode.TOO_MANY_REQUESTS, message); }
    public static BusinessException rateLimited() { return of(ErrorCode.TOO_MANY_REQUESTS); }
    public static BusinessException missing() { return of(ErrorCode.MISSING); }
    public static BusinessException uploadTooLarge() { return of(ErrorCode.REQUEST_TOO_LARGE); }
    public static BusinessException conflict(String message) { return new BusinessException(ErrorCode.DUPLICATE, message); }

    private static BusinessException of(ErrorCode code) {
        return new BusinessException(code, code.message());
    }
}
