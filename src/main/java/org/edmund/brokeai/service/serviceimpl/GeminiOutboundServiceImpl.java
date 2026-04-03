package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.GeminiRequest;
import org.edmund.brokeai.dto.GeminiResponse;
import org.edmund.brokeai.service.GeminiOutboundService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class GeminiOutboundServiceImpl implements GeminiOutboundService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Override
    public GeminiResponse sendToGemini(MultipartFile file, String base64EncodedImage) {

        GeminiRequest request = getGeminiRequest(file, base64EncodedImage);

        String fullUrl = geminiApiUrl + geminiApiKey;
        RestTemplate restTemplate = new RestTemplate();

        System.out.println("🚀 [OUTBOUND] Request sent to Google Gemini API...");

        return restTemplate.postForObject(fullUrl, request, GeminiResponse.class);
    }

    private static GeminiRequest getGeminiRequest(MultipartFile file, String base64EncodedImage) {
        String mimeType = file.getContentType();
        if (mimeType == null) {
            mimeType = "image/jpeg";
        }

        String promptText = "Ekstrak gambar struk ini. Kembalikan HANYA format JSON murni " +
                "dengan key: tanggal (format YYYY-MM-DD), total (angka tanpa titik/koma), " +
                "kategori (tentukan 1 kata, misal: Makanan, Transportasi), dan merchant. Tanpa markdown ```json.";

        GeminiRequest.InlineData inlineData = new GeminiRequest.InlineData(mimeType, base64EncodedImage);
        GeminiRequest.Part textPart = new GeminiRequest.Part(promptText, null);
        GeminiRequest.Part imagePart = new GeminiRequest.Part(null, inlineData);

        GeminiRequest.Content content = new GeminiRequest.Content(List.of(textPart, imagePart));
        GeminiRequest requestBody = new GeminiRequest(List.of(content));
        return requestBody;
    }
}