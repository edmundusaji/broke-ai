package org.edmund.brokeai.service.serviceimpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.edmund.brokeai.dto.AiExpenseResponse;
import org.edmund.brokeai.dto.GeminiRequest;
import org.edmund.brokeai.dto.GeminiResponse;
import org.edmund.brokeai.service.GeminiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;

@Service
public class GeminiServiceImpl implements GeminiService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Override
    public AiExpenseResponse prosesStruk(MultipartFile file) {
        String fullUrl = geminiApiUrl + geminiApiKey;

        try {
            byte[] fileBytes = file.getBytes();
            String base64EncodedImage = Base64.getEncoder().encodeToString(fileBytes);

            GeminiRequest requestBody = getGeminiRequest(file, base64EncodedImage);

            // 5. Kirim HTTP POST ke Google
            RestTemplate restTemplate = new RestTemplate();
            GeminiResponse response = restTemplate.postForObject(fullUrl, requestBody, GeminiResponse.class);

            // 6. Ekstrak teks balasan dan ubah jadi Objek Java
            if (response != null && !response.candidates().isEmpty()) {
                String extractedJsonText = response.candidates().getFirst().content().parts().getFirst().text();

                // Pembersih tambahan jaga-jaga kalau AI ngeyel mengirim Markdown
                extractedJsonText = extractedJsonText.replace("```json", "").replace("```", "").trim();

                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(extractedJsonText, AiExpenseResponse.class);
            }
        } catch (Exception e) {
            System.err.println("Gagal memproses AI: " + e.getMessage());
            e.printStackTrace(); // Tampilkan detail error di terminal agar mudah di-debug
        }

        return new AiExpenseResponse(); // Fallback jika gagal
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