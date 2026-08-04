package org.edmund.brokeai.exception;

import org.edmund.brokeai.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

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
}
