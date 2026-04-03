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

        System.out.println("💾 [EXPENSE SERVICE] Data berhasil disimpan ke Database dengan ID: " + savedTransaction.getId());

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

        // Parse Date
        transaction.setTanggal(parseDate(aiResponse.getTanggal()));

        return transaction;
    }

    /**
     * Metode Defensive Programming khusus untuk Tanggal.
     */
    private LocalDateTime parseDate(String dateFromAI) {
        if (dateFromAI == null || dateFromAI.isBlank()) {
            System.err.println("⚠️ [WARNING] AI did not find any date");
            return LocalDateTime.now(); // Upload time
        }

        try {
            // Target format => YYYY-MM-DD
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate localDate = LocalDate.parse(dateFromAI.trim(), formatter);

            return LocalDateTime.of(localDate, LocalTime.MIDNIGHT);

        } catch (DateTimeParseException e) {
            // If AI response with a weird format (ex: "23 July 2017")
            System.err.println("⚠️ [WARNING] Wrong date format ('" + dateFromAI + "'). Please try again");
            return LocalDateTime.now();
        }
    }
}