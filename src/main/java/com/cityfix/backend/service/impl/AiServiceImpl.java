package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.request.AiAnalyzeRequest;
import com.cityfix.backend.model.dto.response.AiAnalyzeResponse;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.model.entity.SubCategory;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.repository.CategoryRepository;
import com.cityfix.backend.repository.SubCategoryRepository;
import com.cityfix.backend.service.AiService;
import com.cityfix.backend.util.AiPriorityCalculator;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    @Value("${gemini.api.key:GEMINI_API_KEY}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String geminiModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;

    private record MediaData(String mimeType, String base64) {}

    @Override
    public AiAnalyzeResponse analyzeMedia(String mediaUrl) {
        AiAnalyzeRequest request = new AiAnalyzeRequest();
        request.setMediaUrl(mediaUrl);
        return analyze(request);
    }

    @Override
    public AiAnalyzeResponse analyzeMedia(List<String> mediaUrls) {
        AiAnalyzeRequest request = new AiAnalyzeRequest();
        request.setMediaUrls(mediaUrls);
        return analyze(request);
    }

    @Override
    public AiAnalyzeResponse analyze(AiAnalyzeRequest request) {
        try {
            List<String> urls = request.extractAllMediaUrls();
            if (urls.isEmpty()) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Ít nhất một Media URL là bắt buộc");
            }

            // Giới hạn tối đa 5 hình ảnh để đảm bảo dung lượng payload và thời gian phản hồi
            if (urls.size() > 5) {
                log.info("Giới hạn số lượng ảnh phân tích từ {} xuống 5 ảnh đầu tiên", urls.size());
                urls = urls.subList(0, 5);
            }

            // 1. Tải song song tất cả các hình ảnh / video bất đồng bộ
            List<CompletableFuture<MediaData>> downloadFutures = urls.stream()
                    .map(url -> CompletableFuture.supplyAsync(() -> downloadSingleMedia(url)))
                    .toList();

            List<MediaData> downloadedMediaList = downloadFutures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();

            if (downloadedMediaList.isEmpty()) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Không thể tải được nội dung hình ảnh từ danh sách URL đã cung cấp");
            }
            log.info("Đã tải thành công {}/{} tệp media để gửi tới Gemini", downloadedMediaList.size(), urls.size());

            // 2. Lấy danh sách Categories và SubCategories từ Database
            List<Category> categories = categoryRepository.findAllByIsDeletedFalse();
            List<SubCategory> subCategories = subCategoryRepository.findByIsActiveTrueAndIsDeletedFalse();

            StringBuilder categoriesStr = new StringBuilder();
            for (Category c : categories) {
                categoriesStr.append("Nhóm sự cố: ").append(c.getName()).append("\n");
                categoriesStr.append("Danh mục sự cố chi tiết (SubCategories):\n");
                for (SubCategory sc : subCategories) {
                    if (sc.getCategory().getCategoryId().equals(c.getCategoryId())) {
                        categoriesStr.append(" - ").append(sc.getName())
                                .append(" (ID: ").append(sc.getSubCategoryId())
                                .append(", Priority mặc định: ").append(sc.getPriority().name()).append(")\n");
                    }
                }
            }

            // 3. Xây dựng Prompt phân tích đa ảnh và nhận diện đa sự cố (Multi-SubCategory)
            String prompt = "Bạn là chuyên gia thẩm định và đánh giá rủi ro cơ sở hạ tầng đô thị cho hệ thống tiếp nhận phản ánh sự cố CityFix.\n"
                    + "Nhiệm vụ của bạn là phân tích toàn diện (các) hình ảnh/video được cung cấp (có thể bao gồm nhiều góc chụp: cận cảnh vết nứt, toàn cảnh ngã tư, bối cảnh xung quanh của cùng một hiện trường) để:\n"
                    + "1. Xác định xem đây có phải là sự cố cơ sở hạ tầng đô thị hợp lệ hay không (ví dụ: ổ gà, lún đường, vỡ ống nước, nắp cống gãy, ngập úng, cây ngã đổ, đèn đường hỏng, rác ứ đọng...). Nếu KHÔNG hợp lệ (ảnh selfie, văn bản, ảnh phong cảnh không có sự cố, động vật...), trả về 'isValidIssue' = false, 'description' là lý do từ chối bằng TIẾNG VIỆT, các trường khác để null.\n"
                    + "2. Nếu ĐÚNG là sự cố ('isValidIssue' = true), hãy tổng hợp tất cả các góc ảnh và nhận diện TẤT CẢ các danh mục sự cố chi tiết (SubCategories) đang xuất hiện (một hiện trường có thể có đồng thời nhiều sự cố, ví dụ: vừa có 'Ổ gà / lún sụt mặt đường' vừa có 'Nắp cống hư hỏng' hoặc 'Ngập úng').\n"
                    + "Hãy trả về một đối tượng JSON duy nhất chứa các trường sau:\n"
                    + "   - 'subCategoryIds': Mảng danh sách các mã UUID chuẩn 36 ký tự của các SubCategory phù hợp từ danh mục được cung cấp bên dưới (chọn ít nhất 1 SubCategory, có thể chọn nhiều SubCategory nếu hiện trường có đa sự cố).\n"
                    + "   - 'severityScore': Số thực từ 1.0 đến 10.0 đánh giá MỨC ĐỘ HƯ HẠI VẬT LÝ lớn nhất của sự cố (quan sát các ảnh cận cảnh: hố sụt sâu, sụp nắp cống lớn, ống nước xối xả = 8-10; nứt nhẹ, ổ gà nông = 2-4).\n"
                    + "   - 'impact': Số thực từ 1.0 đến 10.0 đánh giá PHẠM VI ẢNH HƯỞNG (quan sát các ảnh góc rộng: ngã tư huyết mạch, đại lộ nhiều làn xe, lưu lượng người đông đúc = 8-10; đường cụt nhỏ, vắng người = 2-4).\n"
                    + "   - 'urgency': Số thực từ 1.0 đến 10.0 đánh giá TÍNH CẤP BÁCH / NGUY CƠ TỨC THÌ (dễ gây tai nạn giao thông, sụp lún bất ngờ, kẹt xe nghiêm trọng = 8-10; có thể xử lý trong vài ngày = 2-4).\n"
                    + "   - 'context': Số thực từ 1.0 đến 10.0 đánh giá BỐI CẢNH XUNG QUANH LÀM TĂNG ĐỘ NGUY HIỂM (gần trường học, bệnh viện, khu chợ, cột điện cao thế, ngập nước che khuất hố = 8-10; bãi đất trống = 2-4).\n"
                    + "   - 'confidenceScore': Số thập phân từ 0 đến 100 thể hiện độ tin cậy của AI.\n"
                    + "   - 'description': Mô tả chi tiết, rõ ràng về hiện trạng các sự cố bằng TIẾNG VIỆT.\n"
                    + "   - 'priorityReasoning': Giải thích ngắn gọn bằng TIẾNG VIỆT lý do chấm 4 điểm số (S, I, U, C) và nhận diện các sự cố dựa trên những gì nhìn thấy từ các hình ảnh.\n"
                    + "LƯU Ý: Chỉ trả về một đối tượng JSON hợp lệ duy nhất, không dùng định dạng markdown ```json ... ```.\n"
                    + "Danh mục SubCategory tham chiếu:\n" + categoriesStr;

            // 4. Chuẩn bị payload gửi Gemini (bao gồm nhiều visual parts + 1 text part)
            List<Map<String, Object>> parts = new ArrayList<>();
            for (MediaData md : downloadedMediaList) {
                Map<String, Object> inlineData = new HashMap<>();
                inlineData.put("mimeType", md.mimeType());
                inlineData.put("data", md.base64());

                Map<String, Object> mediaPart = new HashMap<>();
                mediaPart.put("inlineData", inlineData);
                parts.add(mediaPart);
            }

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);
            parts.add(textPart);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", parts);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(content));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("responseMimeType", "application/json");
            requestBody.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key=" + apiKey;

            // 5. Gửi request sang Gemini
            ResponseEntity<String> response = restTemplate.postForEntity(geminiUrl, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Gemini API call returned non-2xx status: {}", response.getStatusCode());
                return AiAnalyzeResponse.builder()
                        .isValidIssue(false)
                        .description("Không thể kết nối tới dịch vụ AI phân tích. Vui lòng thử lại sau.")
                        .build();
            }

            // Kiểm tra cấu trúc phản hồi an toàn, tránh NullPointerException
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            JsonNode candidates = rootNode.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                log.warn("Gemini API returned no candidates. Possible block reason: {}", rootNode.path("promptFeedback"));
                return AiAnalyzeResponse.builder()
                        .isValidIssue(false)
                        .description("AI không thể phân tích hình ảnh này do chính sách nội dung hoặc hình ảnh không rõ ràng. Vui lòng thử lại với hình ảnh khác.")
                        .build();
            }

            JsonNode partsNode = candidates.get(0).path("content").path("parts");
            if (!partsNode.isArray() || partsNode.isEmpty() || !partsNode.get(0).hasNonNull("text")) {
                log.warn("Gemini response candidate does not contain text parts: {}", candidates.get(0));
                return AiAnalyzeResponse.builder()
                        .isValidIssue(false)
                        .description("Hệ thống AI không thể đọc được nội dung từ hình ảnh. Vui lòng thử lại.")
                        .build();
            }

            String textResponse = partsNode.get(0).path("text").asText().trim();
            // Dọn dẹp markdown code blocks nếu có
            textResponse = textResponse.replace("```json", "").replace("```", "").trim();

            AiAnalyzeResponse aiResponse;
            try {
                aiResponse = objectMapper.readValue(textResponse, AiAnalyzeResponse.class);
            } catch (Exception parseEx) {
                log.error("Failed to parse Gemini JSON response: {}", textResponse, parseEx);
                return AiAnalyzeResponse.builder()
                        .isValidIssue(false)
                        .description("Phản hồi từ AI không đúng định dạng chuẩn. Vui lòng thử lại với hình ảnh khác.")
                        .build();
            }

            if (Boolean.FALSE.equals(aiResponse.getIsValidIssue())) {
                aiResponse.setSubCategoryIds(Collections.emptyList());
                aiResponse.setSubCategoryNames(Collections.emptyList());
                return aiResponse;
            }

            // 6. Xác thực danh sách SubCategories từ Database
            List<UUID> rawSubCatIds = aiResponse.getSubCategoryIds();
            List<UUID> validSubCatIds = new ArrayList<>();
            List<String> validSubCatNames = new ArrayList<>();
            List<Priority> subCatPriorities = new ArrayList<>();

            if (rawSubCatIds != null && !rawSubCatIds.isEmpty()) {
                for (UUID subCatId : rawSubCatIds) {
                    if (subCatId != null) {
                        Optional<SubCategory> subCatOpt = subCategoryRepository.findById(subCatId);
                        if (subCatOpt.isPresent() && subCatOpt.get().isActive() && !subCatOpt.get().isDeleted()) {
                            SubCategory sc = subCatOpt.get();
                            validSubCatIds.add(sc.getSubCategoryId());
                            validSubCatNames.add(sc.getName());
                            subCatPriorities.add(sc.getPriority());
                        }
                    }
                }
            }

            // Phòng ngừa silent failure: Nếu AI nhận định là sự cố (isValidIssue = true)
            // nhưng không tìm thấy SubCategory hợp lệ nào trong Database
            if (validSubCatIds.isEmpty()) {
                log.warn("AI identified valid issue but no matching SubCategory was found in Database. Raw IDs: {}", rawSubCatIds);
                String originalDesc = aiResponse.getDescription() != null ? aiResponse.getDescription() : "Sự cố cơ sở hạ tầng đô thị.";
                return AiAnalyzeResponse.builder()
                        .isValidIssue(false)
                        .subCategoryIds(Collections.emptyList())
                        .subCategoryNames(Collections.emptyList())
                        .description(originalDesc + " (Hệ thống chưa tìm thấy danh mục sự cố phù hợp. Vui lòng chọn hoặc chụp lại ảnh khác rõ ràng và phù hợp với phạm vi hệ thống.)")
                        .priorityReasoning(aiResponse.getPriorityReasoning())
                        .confidenceScore(aiResponse.getConfidenceScore())
                        .severityScore(aiResponse.getSeverityScore())
                        .impact(aiResponse.getImpact())
                        .urgency(aiResponse.getUrgency())
                        .context(aiResponse.getContext())
                        .build();
            }

            aiResponse.setSubCategoryIds(validSubCatIds);
            aiResponse.setSubCategoryNames(validSubCatNames);

            // 7. Thực hiện thuật toán Priority Engine
            // Bước 1: Base Score từ DB (Lấy Base Score cao nhất trong các sự cố để đảm bảo an toàn kỹ thuật)
            double baseScore = AiPriorityCalculator.convertMaxBaseScore(subCatPriorities);

            // Bước 2: Chuẩn hóa 4 điểm số AI (U, I, S, C)
            double S = AiPriorityCalculator.clampScore(aiResponse.getSeverityScore(), 5.0);
            double I = AiPriorityCalculator.clampScore(aiResponse.getImpact(), 5.0);
            double U = AiPriorityCalculator.clampScore(aiResponse.getUrgency(), 5.0);
            double C = AiPriorityCalculator.clampScore(aiResponse.getContext(), 5.0);

            // Bước 3: Tính P = wb*B + ws*S + wi*I + wu*U + wc*C
            double finalScore = AiPriorityCalculator.calculatePriorityScore(baseScore, S, I, U, C);

            // Bước 4: Ánh xạ kết quả (Fuzzy Mapping)
            Priority finalPriority = AiPriorityCalculator.mapScoreToPriority(finalScore);

            // Cập nhật kết quả tính toán vào response
            aiResponse.setBaseScore(baseScore);
            aiResponse.setFinalPriorityScore(finalScore);
            aiResponse.setPriority(finalPriority);
            aiResponse.setSeverityScore(S);
            aiResponse.setImpact(I);
            aiResponse.setUrgency(U);
            aiResponse.setContext(C);

            log.info("AI Analysis completed: SubCategories={}, BaseScore={}, S={}, I={}, U={}, C={} -> FinalScore={} ({})",
                    validSubCatNames, baseScore, S, I, U, C, finalScore, finalPriority);

            return aiResponse;

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Gemini API Error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return AiAnalyzeResponse.builder()
                    .description("Hệ thống AI hiện đang quá tải hoặc gặp sự cố kết nối, vui lòng thử lại sau.")
                    .isValidIssue(false)
                    .build();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to analyze media with AI: {}", e.getMessage(), e);
            return AiAnalyzeResponse.builder()
                    .description("Không thể phân tích hình ảnh vào lúc này. Vui lòng thử lại sau hoặc nhập thông tin thủ công.")
                    .isValidIssue(false)
                    .build();
        }
    }

    private MediaData downloadSingleMedia(String mediaUrl) {
        try {
            URL url = new URL(mediaUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(15000);

            try (InputStream is = connection.getInputStream()) {
                byte[] mediaBytes = is.readAllBytes();
                String base64Media = Base64.getEncoder().encodeToString(mediaBytes).replaceAll("\\s+", "");

                String mimeType = connection.getContentType();
                if (mimeType == null || !mimeType.contains("/")) {
                    String lower = mediaUrl.toLowerCase();
                    if (lower.endsWith(".png")) mimeType = "image/png";
                    else if (lower.endsWith(".webp")) mimeType = "image/webp";
                    else if (lower.endsWith(".mp4")) mimeType = "video/mp4";
                    else mimeType = "image/jpeg";
                } else if (mimeType.contains(";")) {
                    mimeType = mimeType.split(";")[0].trim();
                }

                log.debug("Downloaded media: {} bytes, mimeType: {} from {}", mediaBytes.length, mimeType, mediaUrl);
                return new MediaData(mimeType, base64Media);
            }
        } catch (Exception e) {
            log.warn("Failed to download media from URL '{}': {}", mediaUrl, e.getMessage());
            return null;
        }
    }
}
