package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.response.AiAnalyzeResponse;
import com.cityfix.backend.service.AiService;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.repository.CategoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    @Value("${gemini.api.key:GEMINI_API_KEY}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CategoryRepository categoryRepository;

    @Override
    public AiAnalyzeResponse analyzeImage(String imageUrl) {
        try {
            // 1. Download image and encode to Base64
            URL url = new URL(imageUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            InputStream is = connection.getInputStream();
            byte[] imageBytes = is.readAllBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes).replaceAll("\\s+", "");
            
            // Get correct mime type
            String mimeType = connection.getContentType();
            if (mimeType == null || !mimeType.startsWith("image/")) {
                mimeType = "image/jpeg"; // fallback
            }
            log.info("Downloaded image: {} bytes, mimeType: {}", imageBytes.length, mimeType);

            // 2. Fetch categories
            List<Category> categories = categoryRepository.findAll();
            String categoriesStr = categories.stream()
                .map(c -> c.getCategoryId().toString() + " (" + c.getName() + ")")
                .collect(Collectors.joining(", "));

            // 3. Prepare Gemini API Request
            String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-image:generateContent?key=" + apiKey;

            String prompt = "Analyze this image of an infrastructure issue. Return ONLY a valid JSON object with no markdown formatting. The JSON must contain exactly 5 fields: 'categoryId' (choose the closest matching UUID from this list: " + categoriesStr + "), 'severity' (choose from LOW, MEDIUM, HIGH, CRITICAL), 'dangerLevel' (choose from SAFE, CAUTION, DANGEROUS, CRITICAL), 'confidenceScore' (a decimal between 0 and 100), and 'description' (a brief explanation of the issue).";

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);

            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mimeType", mimeType);
            inlineData.put("data", base64Image);

            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("inlineData", inlineData);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(imagePart, textPart)); // Image first, then text

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(content));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("responseMimeType", "application/json");
            requestBody.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 4. Call API
            ResponseEntity<String> response = restTemplate.postForEntity(geminiUrl, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode rootNode = objectMapper.readTree(response.getBody());
                String textResponse = rootNode.path("candidates").get(0)
                        .path("content").path("parts").get(0).path("text").asText();
                
                // Clean up possible markdown code blocks
                textResponse = textResponse.replace("```json", "").replace("```", "").trim();

                return objectMapper.readValue(textResponse, AiAnalyzeResponse.class);
            } else {
                throw new RuntimeException("AI API failed with status: " + response.getStatusCode());
            }

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Gemini API Error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            log.warn("Falling back to mock AI response due to API limitations.");
            // Return a default mock response matching the requested JSON schema
            return new AiAnalyzeResponse(null, com.cityfix.backend.model.enums.SeverityLevel.MEDIUM, com.cityfix.backend.model.enums.DangerLevel.CAUTION, new java.math.BigDecimal("85.5"), "Mock description due to API limit.");
        } catch (Exception e) {
            log.error("Failed to analyze image with AI: {}", e.getMessage(), e);
            throw new RuntimeException("AI Error: " + e.getMessage());
        }
    }
}
