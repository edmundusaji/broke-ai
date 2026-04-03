package org.edmund.brokeai.service;

import org.edmund.brokeai.dto.AiExpenseResponse;
import org.springframework.web.multipart.MultipartFile;

public interface GeminiService {
    AiExpenseResponse receiptProcess(MultipartFile file);
}
