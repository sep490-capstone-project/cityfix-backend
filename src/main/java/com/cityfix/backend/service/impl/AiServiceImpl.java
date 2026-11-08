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

    @Value("${gemini.model:gemini-3.5-flash}")
    private String geminiModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;

    private record MediaData(String mimeType, String base64) {}

    @Override
    public AiAnalyzeResponse analyzeMedia(String mediaUrl) {
        AiAnalyzeRequest request = new AiAnalyzeRequest();
        if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {
            request.setMediaUrls(List.of(mediaUrl.trim()));
        }
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

            // 3. Xây dựng Prompt phân tích đa ảnh, nhận diện SubCategory chính xác và chấm điểm chuẩn hóa 4 dải
            String prompt = "Bạn là chuyên gia thẩm định hiện trường và đánh giá rủi ro cơ sở hạ tầng đô thị cho hệ thống CityFix.\n"
                    + "Nhiệm vụ của bạn là quan sát khách quan, toàn diện (các) hình ảnh/video được cung cấp (có thể gồm ảnh cận cảnh và toàn cảnh của cùng một hiện trường) để:\n"
                    + "1. KIỂM TRA TÍNH HỢP LỆ:\n"
                    + "   - Xác định đây có phải sự cố cơ sở hạ tầng/môi trường đô thị hợp lệ không (ổ gà, sụt lún, vỡ ống nước, nắp cống hỏng/mất, ngập úng, cây ngã đổ, sự cố điện/đèn đường, rác thải ứ đọng...).\n"
                    + "   - Nếu KHÔNG hợp lệ (ảnh chân dung/selfie, ảnh chụp màn hình văn bản, phong cảnh bình thường không có sự cố, vật nuôi, đồ vật trong nhà...), trả về 'isValidIssue' = false, 'description' giải thích lý do từ chối bằng TIẾNG VIỆT, các trường còn lại để null.\n"
                    + "2. NHẬN DIỆN DANH MỤC SỰ CỐ ('isValidIssue' = true):\n"
                    + "   - Chọn các mã UUID của SubCategory phù hợp nhất từ danh mục bên dưới.\n"
                    + "   - QUY TẮC ĐA SỰ CỐ: Chỉ chọn từ 2 SubCategory trở lên khi hiện trường thực sự có nhiều sự cố ĐỘC LẬP cần quy trình xử lý khác nhau (VD: cây ngã đổ làm đứt dây điện/gãy đèn đường). KHÔNG gán dư các hệ quả phụ nhỏ nhặt (VD: ổ gà nhỏ có đọng ít nước mưa thì CHỈ chọn danh mục ổ gà/hư hỏng mặt đường, KHÔNG chọn thêm 'Ngập úng' trừ khi mặt đường xung quanh bị ngập).\n"
                    + "3. NGUYÊN TẮC CHẤM ĐIỂM CHUẨN HÓA (THANG 1.0 - 10.0, CHỐNG THỔI PHỒNG ĐIỂM):\n"
                    + "   Hệ thống phân loại mức ưu tiên cuối cùng theo ngưỡng nghiêm ngặt: Dưới 6.0 = LOW (Thấp), từ 6.0 đến dưới 8.5 = MEDIUM (Trung bình), từ 8.5 trở lên = HIGH (Cao/Khẩn cấp).\n"
                    + "   Vì vậy bạn PHẢI chấm điểm bám sát bằng chứng thị giác thực tế theo 4 dải chuẩn sau, TUYỆT ĐỐI không chấm vọt lên 8-10 cho sự cố thông thường:\n"
                    + "   - 'severityScore' (MỨC ĐỘ HƯ HẠI VẬT LÝ):\n"
                    + "       + 1.0 - 3.5: Rất nhẹ/thẩm mỹ (vết nứt chân chim, bong tróc sơn, vài túi rác nhỏ trên vỉa hè, cỏ mọc).\n"
                    + "       + 4.0 - 5.9: Hư hại nhỏ - vừa, kết cấu xung quanh còn ổn định (ổ gà nông <5cm diện tích nhỏ, đọng vũng nước nhỏ, 1 bóng đèn đường tắt, rác sinh hoạt ứ đọng cục bộ).\n"
                    + "       + 6.0 - 8.4: Hư hại đáng kể (ổ gà rộng hoặc sâu 5-15cm, mặt đường bong tróc mảng lớn, nắp cống nứt/kênh lệch, cành cây lớn gãy, rò rỉ nước chảy tràn mặt đường, bãi rác tự phát lớn).\n"
                    + "       + 8.5 - 10.0: Hư hại nghiêm trọng/phá hủy kết cấu (hố sụt sâu 'hố tử thần', mất hoặc vỡ sập hoàn toàn nắp cống lộ hố sâu, cột điện nghiêng gãy/dây điện đứt rơi/chập cháy, cây cổ thụ bật gốc chắn ngang đường, vỡ ống nước chính phun mạnh gây xói lở, ngập sâu diện rộng).\n"
                    + "   - 'impact' (PHẠM VI ẢNH HƯỞNG LƯU THÔNG & CỘNG ĐỒNG):\n"
                    + "       + 1.0 - 3.5: Góc khuất, vỉa hè rộng, bãi đất trống, hầu như không cản trở người đi bộ hay xe cộ.\n"
                    + "       + 4.0 - 5.9: Đường hẻm nhỏ, đường nội bộ ít xe, hoặc sát lề đường dễ dàng lách tránh.\n"
                    + "       + 6.0 - 8.4: Chiếm một phần làn xe chạy trên đường phố dân sinh, gây cản trở giao thông hoặc ảnh hưởng nhiều hộ dân xung quanh.\n"
                    + "       + 8.5 - 10.0: Chắn ngang làn chính của trục đường lớn/ngã tư đông đúc, gây ùn tắc nghiêm trọng hoặc chia cắt lối đi.\n"
                    + "   - 'urgency' (TÍNH CẤP BÁCH / NGUY CƠ TAI NẠN TỨC THÌ):\n"
                    + "       + 1.0 - 3.5: Không có nguy cơ gây tai nạn, có thể xử lý theo kế hoạch bảo trì định kỳ.\n"
                    + "       + 4.0 - 5.9: Rủi ro va chạm thấp, người đi đường dễ quan sát từ xa, xử lý trong vài ngày là hợp lý.\n"
                    + "       + 6.0 - 8.4: Tiềm ẩn nguy cơ trượt ngã/va quệt xe máy nếu thiếu chú ý (đặc biệt vào ban đêm/trời mưa), cần xử lý sớm trong 24-48 giờ.\n"
                    + "       + 8.5 - 10.0: NGUY HIỂM TỨC THÌ đe dọa tính mạng, thương vong nặng hoặc cháy nổ/điện giật/sập bẫy giao thông bất ngờ, bắt buộc ứng cứu khẩn cấp ngay lập tức.\n"
                    + "   - 'context' (BỐI CẢNH XUNG QUANH LÀM TĂNG RỦI RO):\n"
                    + "       + 1.0 - 3.5: Khu vực vắng vẻ, tầm nhìn thoáng đãng, ban ngày khô ráo, sự cố nằm ngoài phần đường xe chạy.\n"
                    + "       + 4.0 - 5.9: Khu dân cư bình thường, hoặc ẢNH CHỤP CẬN CẢNH không nhìn thấy rõ bối cảnh đặc biệt xung quanh.\n"
                    + "       + 6.0 - 8.4: Khúc cua khuất tầm nhìn, ban đêm thiếu sáng, mặt đường trơn ướt, hoặc khu phố buôn bán đông người qua lại.\n"
                    + "       + 8.5 - 10.0: Nhìn thấy RÕ RÀNG trong ảnh sự cố nằm ngay cổng trường học/bệnh viện/chợ đông đúc, sát trạm biến áp/đường dây điện trần, hoặc nước ngập đục che khuất hoàn toàn hố sâu/chướng ngại vật.\n"
                    + "   - QUY TẮC CHỐNG SUY DIỄN (QUAN TRỌNG): Chỉ chấm điểm dựa trên những gì NHÌN THẤY TRONG ẢNH/VIDEO. Nếu người dùng chụp cận cảnh không thấy toàn cảnh đường phố hay công trình xung quanh, bắt buộc giữ 'impact' và 'context' ở mức trung lập (4.0 - 5.5 tùy bề mặt vỉa hè hay lòng đường nhựa), TUYỆT ĐỐI không tự suy đoán là đại lộ hay gần trường học/bệnh viện.\n"
                    + "4. ĐỊNH DẠNG JSON ĐẦU RA:\n"
                    + "   Trả về DUY NHẤT một đối tượng JSON hợp lệ (không bọc trong markdown ```json) gồm các trường:\n"
                    + "   - 'isValidIssue': boolean (true/false).\n"
                    + "   - 'subCategoryIds': Mảng các mã UUID chuẩn 36 ký tự từ danh mục tham chiếu (ít nhất 1 mã khi isValidIssue = true).\n"
                    + "   - 'severityScore': Số thực [1.0 - 10.0] (cho phép 1 chữ số thập phân, VD: 4.5, 6.5, 8.5).\n"
                    + "   - 'impact': Số thực [1.0 - 10.0].\n"
                    + "   - 'urgency': Số thực [1.0 - 10.0].\n"
                    + "   - 'context': Số thực [1.0 - 10.0].\n"
                    + "   - 'confidenceScore': Số thực từ 0 đến 100 thể hiện độ tự tin nhận diện sự cố.\n"
                    + "   - 'description': Mô tả khách quan, chi tiết hiện trạng sự cố bằng TIẾNG VIỆT.\n"
                    + "   - 'priorityReasoning': Giải thích ngắn gọn bằng TIẾNG VIỆT căn cứ thị giác cụ thể cho từng điểm S, I, U, C và lý do xếp mức độ rủi ro.\n"
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
            generationConfig.put("temperature", 0.1);
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
