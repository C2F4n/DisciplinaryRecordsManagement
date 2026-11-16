package org.example.disciplinaryrecordsmanagement.handler;

import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.exception.ErrorCode;
import org.example.disciplinaryrecordsmanagement.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> business(BusinessException error) {
        return ResponseEntity.status(error.code().status()).body(Result.error(error.code(), error.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> duplicate() {
        return response(ErrorCode.DUPLICATE);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> validation() {
        return response(ErrorCode.INVALID);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unexpected(Exception error) {
        logUnexpected(error);
        return response(ErrorCode.UNEXPECTED);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) logUnexpected(error);
        return super.handleExceptionInternal(error, Result.error(ErrorCode.forHttpStatus(status)), headers, status, request);
    }

    private static void logUnexpected(Exception error) {
        // SQL and binding exceptions may contain user input. Log the type and location without the raw message.
        LOG.error("Unexpected API failure: type={} locations={}", error.getClass().getName(),
                Arrays.stream(error.getStackTrace()).limit(8).toList());
    }

    private static ResponseEntity<Result<Void>> response(ErrorCode error) {
        return ResponseEntity.status(error.status()).body(Result.error(error));
    }
}
