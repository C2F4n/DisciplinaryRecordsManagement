package org.example.disciplinaryrecordsmanagement.result;

import org.example.disciplinaryrecordsmanagement.exception.ErrorCode;

public record Result<T>(int code, String msg, T data) {
    public static final int SUCCESS = 1;

    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS, "", data);
    }

    public static Result<Void> success() {
        return success(null);
    }

    public static Result<Void> error(ErrorCode code) {
        return error(code, code.message());
    }

    public static Result<Void> error(ErrorCode code, String message) {
        return new Result<>(code.code(), message, null);
    }
}
