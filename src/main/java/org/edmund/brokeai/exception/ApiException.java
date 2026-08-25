package org.edmund.brokeai.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String field;
    private final Integer retryAfterSeconds;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, String field) {
        this(status, code, message, field, null);
    }

    public ApiException(
        HttpStatus status,
        String code,
        String message,
        String field,
        Integer retryAfterSeconds
    ) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
