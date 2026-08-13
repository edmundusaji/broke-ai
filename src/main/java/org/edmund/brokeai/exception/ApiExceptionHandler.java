package org.edmund.brokeai.exception;

import lombok.extern.slf4j.Slf4j;
import org.edmund.brokeai.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    public record ContractError(String code, String message, String field, UUID requestId) {
    }

    public record ErrorEnvelope(ContractError error) {
    }

    @ExceptionHandler(GuestAiTrialLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleGuestAiTrialLimit(GuestAiTrialLimitException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            new ApiErrorResponse(
                HttpStatus.FORBIDDEN.name(),
                GuestAiTrialLimitException.ERROR_CODE,
                exception.getMessage()
            )
        );
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorEnvelope> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(error(
            exception.getCode(),
            exception.getMessage(),
            exception.getField()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException exception) {
        var fieldError = exception.getBindingResult().getFieldError();
        String field = fieldError == null ? null : fieldError.getField();
        String message = fieldError == null ? "The request contains invalid data." : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", message, field));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            error("UNAUTHENTICATED", "Authentication is required.", null)
        );
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingRequestHeaderException.class,
        MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class
    })
    public ResponseEntity<ErrorEnvelope> handleMalformedRequest(Exception exception) {
        return ResponseEntity.badRequest().body(error(
            "VALIDATION_ERROR",
            "The request is missing required data or contains invalid values.",
            null
        ));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorEnvelope> handleOptimisticConflict(ObjectOptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(error(
            "CONFLICTING_UPDATE",
            "This resource was updated by another request.",
            null
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleDataConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(
            "CONFLICTING_UPDATE",
            "The request conflicts with existing data.",
            null
        ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorEnvelope> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String code = status == HttpStatus.UNAUTHORIZED ? "UNAUTHENTICATED" : "VALIDATION_ERROR";
        String message = exception.getReason() == null ? "The request could not be completed." : exception.getReason();
        return ResponseEntity.status(status).body(error(code, message, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception exception) {
        log.error("Unhandled API request failure", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(
            "INTERNAL_ERROR",
            "An unexpected server error occurred.",
            null
        ));
    }

    private ErrorEnvelope error(String code, String message, String field) {
        return new ErrorEnvelope(new ContractError(code, message, field, UUID.randomUUID()));
    }
}
