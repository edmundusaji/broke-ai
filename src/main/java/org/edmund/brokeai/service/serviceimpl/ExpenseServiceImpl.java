package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.AiExpenseResponse;
import org.edmund.brokeai.dto.CategorySummaryDTO;
import org.edmund.brokeai.dto.ExpenseSummaryResponse;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.repository.TransactionRepository;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.service.ExpenseService;
import org.edmund.brokeai.service.GeminiService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    private final GeminiService geminiService;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    @Override
    public Transaction saveReceipt(MultipartFile file) {
        AppUser currentUser = currentUserService.getCurrentUser();
        AiExpenseResponse aiResponse = geminiService.receiptProcess(file);

        if (aiResponse == null || aiResponse.getTotal() == null) {
            throw new RuntimeException("Failed to process receipt");
        }

        Transaction transaction = mapToEntity(aiResponse, "RECEIPT", currentUser);
        return transactionRepository.save(transaction);
    }

    @Override
    public Transaction saveNotification(String notification) {
        AppUser currentUser = currentUserService.getCurrentUser();
        AiExpenseResponse aiResponse = geminiService.prosesNotifikasi(notification);

        if (aiResponse == null || aiResponse.getTotal() == null) {
            throw new RuntimeException("Failed to process notifications");
        }

        Transaction transaction = mapToEntity(aiResponse, "NOTIFICATION", currentUser);
        return transactionRepository.save(transaction);
    }

    @Override
    public ExpenseSummaryResponse getExpenseSummary(int month, int year) {
        AppUser currentUser = currentUserService.getCurrentUser();
        DateRange dateRange = buildMonthDateRange(month, year);

        List<CategorySummaryDTO> breakdown = transactionRepository.getExpenseSummaryByUserAndDateRange(
            currentUser,
            dateRange.startDate(),
            dateRange.endDate()
        );

        Double total = breakdown.stream()
            .mapToDouble(CategorySummaryDTO::totalAmount)
            .sum();

        return new ExpenseSummaryResponse(total, breakdown);
    }

    @Override
    public List<Transaction> getExpenseHistory(int month, int year) {
        AppUser currentUser = currentUserService.getCurrentUser();
        DateRange dateRange = buildMonthDateRange(month, year);

        return transactionRepository.findByUserAndTanggalBetweenOrderByTanggalDesc(
            currentUser,
            dateRange.startDate(),
            dateRange.endDate()
        );
    }

    private Transaction mapToEntity(AiExpenseResponse aiResponse, String tipeInput, AppUser user) {
        Transaction transaction = new Transaction();

        transaction.setJumlah(aiResponse.getTotal());
        transaction.setKategori(aiResponse.getKategori());
        transaction.setMerchant(aiResponse.getMerchant());
        transaction.setTipeInput(tipeInput);
        transaction.setStatusValidasi("PENDING");
        transaction.setUser(user);
        transaction.setTanggal(parseDateAndTime(aiResponse.getTanggal(), aiResponse.getWaktu()));

        return transaction;
    }

    private DateRange buildMonthDateRange(int month, int year) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime startDate = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59);
        return new DateRange(startDate, endDate);
    }

    private LocalDateTime parseDateAndTime(String dateFromAI, String timeFromAI) {
        if (dateFromAI == null || dateFromAI.isBlank()) {
            return LocalDateTime.now();
        }

        LocalDate localDate;
        try {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            localDate = LocalDate.parse(dateFromAI.trim(), dateFormatter);
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }

        LocalTime localTime;
        if (timeFromAI == null || timeFromAI.isBlank() || timeFromAI.equalsIgnoreCase("null")) {
            localTime = LocalTime.now();
        } else {
            try {
                localTime = LocalTime.parse(timeFromAI.trim());
            } catch (DateTimeParseException e) {
                localTime = LocalTime.now();
            }
        }

        return LocalDateTime.of(localDate, localTime);
    }

    private record DateRange(LocalDateTime startDate, LocalDateTime endDate) {
    }
}
