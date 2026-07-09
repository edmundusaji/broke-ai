package org.edmund.brokeai.dto;

import java.util.List;

/**
 * DTO ini adalah paket lengkap yang akan dikirim ke HP.
 * Berisi total pengeluaran bulan ini, dan rincian per kategorinya.
 */
public record ExpenseSummaryResponse(
    Double totalExpense,
    List<CategorySummaryDTO> categoryBreakdown
) {}