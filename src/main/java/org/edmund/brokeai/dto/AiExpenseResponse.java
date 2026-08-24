package org.edmund.brokeai.dto;

import lombok.Data;

@Data
public class AiExpenseResponse {
    private Boolean isExpense;
    private Double confidence;
    private String date;
    private String time;
    private Double amount;
    private String category;
    private String paymentMethod;
    private String description;
}
