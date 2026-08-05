package org.edmund.brokeai.dto;

/** DTO for a category summary, for example: "Food", 150000.0. */
public record CategorySummaryDTO(String category, Double totalAmount) {}
