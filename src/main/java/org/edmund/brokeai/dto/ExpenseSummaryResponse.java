package org.edmund.brokeai.dto;

import java.util.List;

public record ExpenseSummaryResponse(
    Double totalExpense,
    List<CategorySummaryDTO> categoryBreakdown
) {}