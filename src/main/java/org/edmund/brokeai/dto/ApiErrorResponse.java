package org.edmund.brokeai.dto;

public record ApiErrorResponse(
    String status,
    String code,
    String message
) {
}
