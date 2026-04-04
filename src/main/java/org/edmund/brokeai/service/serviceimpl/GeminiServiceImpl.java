package org.edmund.brokeai.service.serviceimpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.AiExpenseResponse;
import org.edmund.brokeai.dto.GeminiRequest;
import org.edmund.brokeai.dto.GeminiResponse;
import org.edmund.brokeai.service.GeminiOutboundService;
import org.edmund.brokeai.service.GeminiService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;

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
            System.err.println("Failed to Process AI (Receipt): " + e.getMessage());
            e.printStackTrace();
        }
        return new AiExpenseResponse();
    }

    // Notification Entry Point
    @Override
    public AiExpenseResponse prosesNotifikasi(String notificationText) {
        try {
            GeminiRequest request = buildTextRequest(notificationText);
            return executeAndParse(request);
        } catch (Exception e) {
            System.err.println("Failed to Process AI (Notification): " + e.getMessage());
            e.printStackTrace();
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
                "with key: tanggal (format YYYY-MM-DD), total (number without dot/comma), " +
                "kategori (decide 1 word, ex: Food, Transportation, Top-Up), merchant, and waktu(format HH:mm:ss), " +
                "if time not found return null. Without markdown ```json.";

        GeminiRequest.InlineData inlineData = new GeminiRequest.InlineData(mimeType, base64EncodedImage);
        GeminiRequest.Part textPart = new GeminiRequest.Part(promptText, null);
        GeminiRequest.Part imagePart = new GeminiRequest.Part(null, inlineData);

        GeminiRequest.Content content = new GeminiRequest.Content(List.of(textPart, imagePart));
        return new GeminiRequest(List.of(content));
    }

    private static GeminiRequest buildTextRequest(String notificationText) {
        String promptText = "Extract this transaction text notification. Return ONLY in pure JSON format " +
                "with key: tanggal (format YYYY-MM-DD), total (number without dot/comma), " +
                "kategori (decide 1 word, ex: Food, Transportation, Top-Up), merchant, " +
                "dan waktu (format HH:mm:ss), if time not found return null. Without markdown ```json.\n\n" +
                "Notification Text: " + notificationText;

        GeminiRequest.Part textPart = new GeminiRequest.Part(promptText, null);
        GeminiRequest.Content content = new GeminiRequest.Content(List.of(textPart));
        return new GeminiRequest(List.of(content));
    }
}