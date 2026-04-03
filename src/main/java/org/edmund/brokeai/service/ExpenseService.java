package org.edmund.brokeai.service;

import org.edmund.brokeai.entity.Transaction;
import org.springframework.web.multipart.MultipartFile;

/**
 * Call GeminiService to interact with TransactionRepository.
 */
public interface ExpenseService {
    Transaction saveReceipt(MultipartFile file);
}