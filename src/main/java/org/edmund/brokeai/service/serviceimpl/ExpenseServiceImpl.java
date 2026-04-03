package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.AiExpenseResponse;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.repository.TransactionRepository;
import org.edmund.brokeai.service.ExpenseService;
import org.edmund.brokeai.service.GeminiService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    // This will call:
    //       1. GeminiService (Read the photos)
    //       2. TransactionRepository (Save to Database)
    private final GeminiService geminiService;
    private final TransactionRepository transactionRepository;

    @Override
    public Transaction saveReceipt(MultipartFile file) {

        System.out.println("🚀 [EXPENSE SERVICE] Processing receipt...");

        // Read Logic in GeminiService
        AiExpenseResponse aiResponse = geminiService.receiptProcess(file);

        if (aiResponse == null || aiResponse.getTotal() == null) {
            throw new RuntimeException("⛔ [EXPENSE SERVICE] Failed to process receipt");
        }

        System.out.println("✅ [EXPENSE SERVICE] Extract Completed: " + aiResponse.getMerchant());

        Transaction transaction = mapToEntity(aiResponse);

        // Save to PostgreSQL
        Transaction savedTransaction = transactionRepository.save(transaction);

        System.out.println("💾 [EXPENSE SERVICE] Data successfully saved with ID : " + savedTransaction.getId());

        return savedTransaction;
    }

    /**
     * Logic Mapping to Entity
     */
    private Transaction mapToEntity(AiExpenseResponse aiResponse) {
        Transaction transaction = new Transaction();

        transaction.setJumlah(aiResponse.getTotal());
        transaction.setKategori(aiResponse.getKategori());
        transaction.setMerchant(aiResponse.getMerchant());

        // Hardcode because the input is a file
        transaction.setTipeInput("RECEIPT");

        // PENDING as default, change later in the dashboard
        transaction.setStatusValidasi("PENDING");

        // Parse Date + Time
        transaction.setTanggal(parseDateAndTime(aiResponse.getTanggal(), aiResponse.getWaktu()));

        return transaction;
    }

    /**
     * Metode Defensive Programming khusus untuk Tanggal.
     */
    private LocalDateTime parseDateAndTime(String dateFromAI, String timeFromAI) {
        // Date
        if (dateFromAI == null || dateFromAI.isBlank()) {
            System.err.println("⚠️ [WARNING] AI did not find any date. Falling back to CURRENT time.");
            return LocalDateTime.now();
        }

        LocalDate localDate;
        try {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            localDate = LocalDate.parse(dateFromAI.trim(), dateFormatter);
        } catch (DateTimeParseException e) {
            System.err.println("⚠️ [WARNING] Wrong date format ('" + dateFromAI + "'). Falling back to CURRENT time.");
            return LocalDateTime.now();
        }

        // Time
        LocalTime localTime;
        if (timeFromAI == null || timeFromAI.isBlank() || timeFromAI.equalsIgnoreCase("null")) {
            System.out.println("⚠️ [INFO] AI did not find time. Using current device upload time.");
            localTime = LocalTime.now(); // Default upload time
        } else {
            try {
                localTime = LocalTime.parse(timeFromAI.trim());
            } catch (DateTimeParseException e) {
                System.err.println("⚠️ [WARNING] Wrong time format ('" + timeFromAI + "'). Using current device upload time.");
                localTime = LocalTime.now();
            }
        }

        // Combine
        return LocalDateTime.of(localDate, localTime);
    }
}