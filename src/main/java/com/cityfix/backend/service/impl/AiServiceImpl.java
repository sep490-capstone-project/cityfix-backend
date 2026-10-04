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
    private final com.cityfix.backend.repository.SubCategoryRepository subCategoryRepository;

    @Override
    public AiAnalyzeResponse analyzeMedia(String mediaUrl) {
        try {
            // 1. Download media and encode to Base64
            URL url = new URL(mediaUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            InputStream is = connection.getInputStream();
            byte[] mediaBytes = is.readAllBytes();
            String base64Media = Base64.getEncoder().encodeToString(mediaBytes).replaceAll("\\s+", "");
            
            // Get correct mime type
            String mimeType = connection.getContentType();
            if (mimeType == null) {
                mimeType = "application/octet-stream"; // fallback
            }
            log.info("Downloaded media: {} bytes, mimeType: {}", mediaBytes.length, mimeType);

            // 2. Fetch categories and subcategories
            List<Category> categories = categoryRepository.findAll();
            List<com.cityfix.backend.model.entity.SubCategory> subCategories = subCategoryRepository.findByIsActiveTrue();

            StringBuilder categoriesStr = new StringBuilder();
            for (Category c : categories) {
                categoriesStr.append("Category: ").append(c.getName()).append(" (ID: ").append(c.getCategoryId()).append(")\n");
                categoriesStr.append("SubCategories:\n");
                for (com.cityfix.backend.model.entity.SubCategory sc : subCategories) {
                    if (sc.getCategory().getCategoryId().equals(c.getCategoryId())) {
                        categoriesStr.append(" - ").append(sc.getName())
                                .append(" (ID: ").append(sc.getSubCategoryId())
                                .append(", Priority: ").append(sc.getPriority().name()).append(")\n");
                    }
                }
            }

            // 3. Prepare Gemini API Request
            String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + apiKey;
            String prompt = "Phân tích hình ảnh hoặc video này để xác định xem nó có phải là một sự cố cơ sở hạ tầng (ví dụ: đường hỏng, rò rỉ nước, v.v.) hay không. Nếu KHÔNG phải là sự cố cơ sở hạ tầng (hình ảnh/video không liên quan), hãy trả về 'isValidIssue' là false, 'description' là lời giải thích lý do từ chối bằng TIẾNG VIỆT, và các trường khác để null. Nếu ĐÚNG là sự cố cơ sở hạ tầng, trả về 'isValidIssue' là true và cung cấp các thông tin sau. Chỉ trả về một đối tượng JSON hợp lệ không có định dạng markdown, chứa các trường: 'isValidIssue' (true/false), 'categoryId' (BẮT BUỘC chỉ trả về mã UUID chuẩn 36 ký tự từ danh sách Category), 'subCategoryId' (BẮT BUỘC trả về UUID chuẩn 36 ký tự từ danh sách SubCategories thuộc Category đã chọn), 'severity' (chọn từ LOW, MEDIUM, HIGH, CRITICAL), 'priority' (Đánh giá mức độ nguy hiểm của sự cố từ hình ảnh (AI_Priority: LOW, MEDIUM, HIGH). Sau đó so sánh với Priority mặc định của SubCategory đó trong danh sách. Trả về mức cao nhất giữa AI_Priority và SubCategory Priority theo quy tắc HIGH > MEDIUM > LOW), 'confidenceScore' (số thập phân từ 0 đến 100), và 'description' (mô tả ngắn gọn về sự cố bằng TIẾNG VIỆT).\nDanh sách Category và SubCategory:\n" + categoriesStr.toString();

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);

            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mimeType", mimeType);
            inlineData.put("data", base64Media);

            Map<String, Object> mediaPart = new HashMap<>();
            mediaPart.put("inlineData", inlineData);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(mediaPart, textPart)); // Media first, then text

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

                AiAnalyzeResponse aiResponse = objectMapper.readValue(textResponse, AiAnalyzeResponse.class);
                
                if (aiResponse.getCategoryId() != null) {
                    categoryRepository.findById(aiResponse.getCategoryId())
                            .ifPresent(category -> aiResponse.setCategoryName(category.getName()));
                }
                
                if (aiResponse.getSubCategoryId() != null) {
                    subCategoryRepository.findById(aiResponse.getSubCategoryId())
                            .ifPresent(subCategory -> aiResponse.setSubCategoryName(subCategory.getName()));
                }
                return aiResponse;
            } else {
                throw new RuntimeException("AI API failed with status: " + response.getStatusCode());
            }

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Gemini API Error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            log.warn("Falling back to mock AI response due to API limitations or server errors.");
            // Return a default mock response matching the requested JSON schema
            return new AiAnalyzeResponse(null, null, null, null, null, null, null, "Hệ thống AI hiện đang quá tải, vui lòng thử lại sau.", false);
        } catch (Exception e) {
            log.error("Failed to analyze media with AI: {}", e.getMessage(), e);
            throw new RuntimeException("AI Error: " + e.getMessage());
        }
    }
}
