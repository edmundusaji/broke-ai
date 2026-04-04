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

    @Override
    public AiExpenseResponse receiptProcess(MultipartFile file) {

        try {
            byte[] fileBytes = file.getBytes();
            String base64EncodedImage = Base64.getEncoder().encodeToString(fileBytes);
            GeminiRequest request = getGeminiRequest(file, base64EncodedImage);

            GeminiResponse response = geminiOutboundService.sendToGemini(request);

            // Response => Object
            if (response != null && !response.candidates().isEmpty()) {
                String extractedJsonText = response.candidates().getFirst().content().parts().getFirst().text();

                // If the AI sends strings
                extractedJsonText = extractedJsonText.replace("```json", "").replace("```", "").trim();

                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(extractedJsonText, AiExpenseResponse.class);
            }
        } catch (Exception e) {
            System.err.println("Failed to Process AI: " + e.getMessage());
            e.printStackTrace();
        }

        return new AiExpenseResponse();
    }

    private static GeminiRequest getGeminiRequest(MultipartFile file, String base64EncodedImage) {
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
        GeminiRequest requestBody = new GeminiRequest(List.of(content));
        return requestBody;
    }

    @Override
    public AiExpenseResponse prosesNotifikasi(String notificationText) {
        try {
            GeminiRequest request = sendTextToGemini(notificationText);
            GeminiResponse response = geminiOutboundService.sendToGemini(request);

            if (response != null && !response.candidates().isEmpty()) {
                String extractedJsonText = response.candidates().getFirst().content().parts().getFirst().text();
                extractedJsonText = extractedJsonText.replace("```json", "").replace("```", "").trim();

                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(extractedJsonText, AiExpenseResponse.class);
            }
        } catch (Exception e) {
            System.err.println("Gagal memproses AI (Notifikasi): " + e.getMessage());
            e.printStackTrace();
        }
        return new AiExpenseResponse();
    }

    public GeminiRequest sendTextToGemini(String notificationText) {
        String promptText = "Extract this transaction text notification. eturn ONLY in pure JSON format " +
                "with key: tanggal (format YYYY-MM-DD), total (number without dot/comma), " +
                "kategori (decide 1 word, ex: Food, Transportation, Top-Up), merchant, " +
                "dan waktu (format HH:mm:ss), if time not found return null.  Without markdown ```json.\n\n" +
                "Teks Notifikasi: " + notificationText;

        GeminiRequest.Part textPart = new GeminiRequest.Part(promptText, null);
        GeminiRequest.Content content = new GeminiRequest.Content(List.of(textPart));
        GeminiRequest request = new GeminiRequest(List.of(content));

        return request;
    }
}