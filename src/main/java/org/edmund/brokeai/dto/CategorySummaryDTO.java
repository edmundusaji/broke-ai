package org.edmund.brokeai.dto;

/**
 * DTO ini merepresentasikan irisan kue pada Pie Chart nantinya.
 * Contoh: "Makanan", 150000.0
 */
public record CategorySummaryDTO(String kategori, Double totalAmount) {}