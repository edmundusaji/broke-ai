package org.edmund.brokeai.service.serviceimpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.edmund.brokeai.dto.AiExpenseResponse;
import org.edmund.brokeai.dto.GeminiRequest;
import org.edmund.brokeai.dto.GeminiResponse;
import org.edmund.brokeai.service.GeminiOutboundService;
import org.edmund.brokeai.service.GeminiService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiServiceImpl implements GeminiService {

    private final GeminiOutboundService geminiOutboundService;

    // Receipt Entry Point
    @Override
    public AiExpenseResponse receiptProcess(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            String base64EncodedImage = Base64.getEncoder().encodeToString(fileBytes);
            GeminiRequest request = buildImageRequest(file, base64EncodedImage);
            return executeAndParse(request);
        } catch (Exception e) {
            log.error("AI receipt processing failed ({})", e.getClass().getSimpleName());
        }
        return new AiExpenseResponse();
    }

    // Notification Entry Point
    @Override
    public AiExpenseResponse processNotification(String notificationText) {
        try {
            GeminiRequest request = buildTextRequest(notificationText);
            return executeAndParse(request);
        } catch (Exception e) {
            log.error("AI notification processing failed ({})", e.getClass().getSimpleName());
        }
        return new AiExpenseResponse();
    }

    /**
     * Send request to Outbound Logic
     * (DRY Principle).
     */
    private AiExpenseResponse executeAndParse(GeminiRequest request) throws Exception {
        GeminiResponse response = geminiOutboundService.sendToGemini(request);

        // Parse Response => Object
        if (response != null && !response.candidates().isEmpty()) {
            String extractedJsonText = response.candidates().getFirst().content().parts().getFirst().text();

            // Markdown clean-up (if any)
            extractedJsonText = extractedJsonText.replace("```json", "").replace("```", "").trim();

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(extractedJsonText, AiExpenseResponse.class);
        }

        return new AiExpenseResponse();
    }

    private static GeminiRequest buildImageRequest(MultipartFile file, String base64EncodedImage) {
        String mimeType = file.getContentType();
        if (mimeType == null) {
            mimeType = "image/jpeg";
        }

        String promptText = "Extract this receipt image. Return ONLY in pure JSON format " +
                "with keys: date (format YYYY-MM-DD), time (format HH:mm:ss), " +
                "amount (number without thousands separators), category (one concise word, ex: Food, Transportation, Top-Up), " +
                "paymentMethod (the payment provider or rail, ex: GoPay, OVO, Bank BCA, Akulaku, QRIS), " +
                "description (a concise transaction purpose or purchased item, ex: Coffee Purchase, KitaBisa Donation, Cold Medicine), " +
                "Do not use the storefront or merchant name as paymentMethod. " +
                "If time is not found, return null. Do not wrap the JSON in markdown.";

        GeminiRequest.InlineData inlineData = new GeminiRequest.InlineData(mimeType, base64EncodedImage);
        GeminiRequest.Part textPart = new GeminiRequest.Part(promptText, null);
        GeminiRequest.Part imagePart = new GeminiRequest.Part(null, inlineData);

        GeminiRequest.Content content = new GeminiRequest.Content(List.of(textPart, imagePart));
        return new GeminiRequest(List.of(content));
    }

    private static GeminiRequest buildTextRequest(String notificationText) {
        LocalDate today = LocalDate.now();
        String promptText = "Extract this transaction text notification. Return ONLY in pure JSON format " +
                "with keys: date (format YYYY-MM-DD), time (format HH:mm:ss), " +
                "amount (number without thousands separators), category (one concise word, ex: Food, Transportation, Top-Up), " +
                "paymentMethod (the payment provider or rail, ex: GoPay, OVO, Bank BCA, Akulaku, QRIS), " +
                "description (a concise transaction purpose or purchased item, ex: Coffee Purchase, KitaBisa Donation, Cold Medicine), " +
                "Do not use the storefront or merchant name as paymentMethod. " +
                "System Context: Today's date is " + today + ". " +
                "If the provided text does not contain any explicit date, strictly return today's date as date. " +
                "If time is not found, return null. Do not wrap the JSON in markdown.\n\n" +
                "Notification Text: " + notificationText;

        GeminiRequest.Part textPart = new GeminiRequest.Part(promptText, null);
        GeminiRequest.Content content = new GeminiRequest.Content(List.of(textPart));
        return new GeminiRequest(List.of(content));
    }
}
