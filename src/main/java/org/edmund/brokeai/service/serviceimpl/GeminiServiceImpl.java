package org.edmund.brokeai.service.serviceimpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.AiExpenseResponse;
import org.edmund.brokeai.dto.GeminiResponse;
import org.edmund.brokeai.service.GeminiOutboundService;
import org.edmund.brokeai.service.GeminiService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

@Service
@RequiredArgsConstructor
public class GeminiServiceImpl implements GeminiService {

    private final GeminiOutboundService geminiOutboundService;

    @Override
    public AiExpenseResponse receiptProcess(MultipartFile file) {

        try {
            byte[] fileBytes = file.getBytes();
            String base64EncodedImage = Base64.getEncoder().encodeToString(fileBytes);

            GeminiResponse response = geminiOutboundService.sendToGemini(file, base64EncodedImage);

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

    @Override
    public AiExpenseResponse prosesNotifikasi(String teksNotifikasi) {
        try {
            GeminiResponse response = geminiOutboundService.sendTextToGemini(teksNotifikasi);

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
}