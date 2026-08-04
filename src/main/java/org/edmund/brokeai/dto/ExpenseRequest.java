package org.edmund.brokeai.dto;

import java.time.LocalDate;

/** Payload for manually creating or updating an expense. */
public record ExpenseRequest(
    LocalDate date,
    Double amount,
    String category,
    String paymentMethod,
    String description
) {
}
