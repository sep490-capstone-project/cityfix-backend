package com.cityfix.backend.service.impl;

import com.cityfix.backend.model.dto.request.AiAnalyzeRequest;
import com.cityfix.backend.model.dto.response.AiAnalyzeResponse;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.model.entity.SubCategory;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.repository.CategoryRepository;
import com.cityfix.backend.repository.SubCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SubCategoryRepository subCategoryRepository;

    @Mock
    private RestTemplate restTemplate;

    private AiServiceImpl aiService;

    private Category catGiaoThong;
    private Category catKhac;
    private SubCategory subSutLun;
    private SubCategory subKhac;

    @BeforeEach
    void setUp() {
        aiService = new AiServiceImpl(categoryRepository, subCategoryRepository) {
            @Override
            MediaData downloadSingleMedia(String mediaUrl) {
                return new MediaData("image/jpeg", "dGVzdA==");
            }
        };
        ReflectionTestUtils.setField(aiService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(aiService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(aiService, "geminiModel", "gemini-3.5-flash");

        catGiaoThong = Category.builder()
                .categoryId(UUID.randomUUID())
                .name("Hạ tầng giao thông")
                .description("Sự cố đường bộ, vỉa hè, cầu cống")
                .isActive(true)
                .build();

        catKhac = Category.builder()
                .categoryId(UUID.randomUUID())
                .name("Khác")
                .description("Các nhóm sự cố hạ tầng, môi trường đô thị khác")
                .isActive(true)
                .build();

        subSutLun = SubCategory.builder()
                .subCategoryId(UUID.randomUUID())
                .category(catGiaoThong)
                .name("Sụt lún mặt đường / Ổ gà")
                .description("Mặt đường nhựa bị bong tróc, sụt lún, xuất hiện hố ổ gà gây nguy hiểm")
                .priority(Priority.HIGH)
                .sla(24)
                .isActive(true)
                .build();

        subKhac = SubCategory.builder()
                .subCategoryId(UUID.randomUUID())
                .category(catKhac)
                .name("Khác")
                .description("Sự cố đô thị hợp lệ nhưng có đặc điểm tương đồng dưới 70% so với các danh mục hiện có")
                .priority(Priority.LOW)
                .sla(48)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Khi độ tương đồng >= 70% (85%), hệ thống giữ nguyên SubCategory cụ thể phù hợp và đưa mô tả đặc điểm vào Prompt")
    void analyze_whenSimilarityAbove70Percent_returnsMatchedSpecificSubCategory() {
        when(categoryRepository.findAllByIsDeletedFalse()).thenReturn(List.of(catGiaoThong, catKhac));
        when(subCategoryRepository.findByIsActiveTrueAndIsDeletedFalse()).thenReturn(List.of(subSutLun, subKhac));
        when(subCategoryRepository.findById(subSutLun.getSubCategoryId())).thenReturn(Optional.of(subSutLun));

        String geminiJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"isValidIssue\\": true, \\"subCategoryIds\\": [\\"%s\\"], \\"severityScore\\": 6.5, \\"impact\\": 6.0, \\"urgency\\": 6.5, \\"context\\": 5.0, \\"confidenceScore\\": 85.0, \\"description\\": \\"Ổ gà sâu trên mặt đường nhựa\\", \\"priorityReasoning\\": \\"Khớp rõ đặc điểm ổ gà mặt đường\\"}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """, subSutLun.getSubCategoryId());

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(geminiJson, HttpStatus.OK));

        AiAnalyzeRequest request = new AiAnalyzeRequest();
        request.setMediaUrls(List.of("https://example.com/pothole.jpg"));

        AiAnalyzeResponse response = aiService.analyze(request);

        assertThat(response.getIsValidIssue()).isTrue();
        assertThat(response.getSubCategoryIds()).containsExactly(subSutLun.getSubCategoryId());
        assertThat(response.getSubCategoryNames()).containsExactly("Sụt lún mặt đường / Ổ gà");
        assertThat(response.getConfidenceScore()).isEqualByComparingTo(BigDecimal.valueOf(85.0));

        // Kiểm tra Prompt gửi sang Gemini có chứa Đặc điểm nhận diện và quy tắc 70%
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), entityCaptor.capture(), eq(String.class));
        String requestPayloadStr = entityCaptor.getValue().getBody().toString();
        assertThat(requestPayloadStr).contains("Đặc điểm nhận diện: Mặt đường nhựa bị bong tróc, sụt lún");
        assertThat(requestPayloadStr).contains("70%");
        assertThat(requestPayloadStr).contains(subKhac.getSubCategoryId().toString());
    }

    @Test
    @DisplayName("Khi độ tương đồng < 70% (55%) dù AI vẫn trả về UUID cụ thể, Backend tự động chuyển sang SubCategory 'Khác'")
    void analyze_whenSimilarityBelow70PercentEvenWithSpecificSubCategory_switchesToOtherSubCategory() {
        when(categoryRepository.findAllByIsDeletedFalse()).thenReturn(List.of(catGiaoThong, catKhac));
        when(subCategoryRepository.findByIsActiveTrueAndIsDeletedFalse()).thenReturn(List.of(subSutLun, subKhac));
        when(subCategoryRepository.findById(subSutLun.getSubCategoryId())).thenReturn(Optional.of(subSutLun));

        String geminiJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"isValidIssue\\": true, \\"subCategoryIds\\": [\\"%s\\"], \\"severityScore\\": 5.0, \\"impact\\": 4.5, \\"urgency\\": 4.5, \\"context\\": 4.5, \\"confidenceScore\\": 55.0, \\"description\\": \\"Biển báo công cộng bị rỉ sét nhẹ\\", \\"priorityReasoning\\": \\"Đặc điểm chỉ giống một phần nhỏ với hạ tầng giao thông\\"}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """, subSutLun.getSubCategoryId());

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(geminiJson, HttpStatus.OK));

        AiAnalyzeRequest request = new AiAnalyzeRequest();
        request.setMediaUrls(List.of("https://example.com/rusty-sign.jpg"));

        AiAnalyzeResponse response = aiService.analyze(request);

        assertThat(response.getIsValidIssue()).isTrue();
        assertThat(response.getSubCategoryIds()).containsExactly(subKhac.getSubCategoryId());
        assertThat(response.getSubCategoryNames()).containsExactly("Khác");
        assertThat(response.getConfidenceScore()).isEqualByComparingTo(BigDecimal.valueOf(55.0));
    }

    @Test
    @DisplayName("Khi AI chọn trực tiếp SubCategory 'Khác' nhưng trả về confidenceScore >= 70, hệ thống chuẩn hóa confidenceScore < 70")
    void analyze_whenAiDirectlySelectsOtherSubCategory_normalizesConfidenceBelow70() {
        when(categoryRepository.findAllByIsDeletedFalse()).thenReturn(List.of(catGiaoThong, catKhac));
        when(subCategoryRepository.findByIsActiveTrueAndIsDeletedFalse()).thenReturn(List.of(subSutLun, subKhac));
        when(subCategoryRepository.findById(subKhac.getSubCategoryId())).thenReturn(Optional.of(subKhac));

        String geminiJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"isValidIssue\\": true, \\"subCategoryIds\\": [\\"%s\\"], \\"severityScore\\": 5.5, \\"impact\\": 5.0, \\"urgency\\": 5.0, \\"context\\": 4.5, \\"confidenceScore\\": 80.0, \\"description\\": \\"Ghế đá công viên bị gãy\\", \\"priorityReasoning\\": \\"Sự cố công cộng chưa có trong danh mục cụ thể\\"}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """, subKhac.getSubCategoryId());

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(geminiJson, HttpStatus.OK));

        AiAnalyzeRequest request = new AiAnalyzeRequest();
        request.setMediaUrls(List.of("https://example.com/broken-bench.jpg"));

        AiAnalyzeResponse response = aiService.analyze(request);

        assertThat(response.getIsValidIssue()).isTrue();
        assertThat(response.getSubCategoryIds()).containsExactly(subKhac.getSubCategoryId());
        assertThat(response.getSubCategoryNames()).containsExactly("Khác");
        assertThat(response.getConfidenceScore().doubleValue()).isLessThan(70.0);
    }

    @Test
    @DisplayName("Khi DB chưa có SubCategory 'Khác' và độ tương đồng < 70%, hệ thống tự động khởi tạo Category/SubCategory 'Khác' và gán về 'Khác'")
    void analyze_whenOtherSubCategoryMissingInDbAndBelow70_autoCreatesAndAssignsOther() {
        when(categoryRepository.findAllByIsDeletedFalse()).thenReturn(List.of(catGiaoThong));
        when(subCategoryRepository.findByIsActiveTrueAndIsDeletedFalse()).thenReturn(List.of(subSutLun));
        when(subCategoryRepository.findByNameAndIsDeletedFalse("Khác")).thenReturn(Optional.empty());
        when(categoryRepository.findByNameAndIsDeletedFalse("Khác")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(catKhac);
        when(subCategoryRepository.save(any(SubCategory.class))).thenReturn(subKhac);
        when(subCategoryRepository.findById(subSutLun.getSubCategoryId())).thenReturn(Optional.of(subSutLun));

        String geminiJson = String.format("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"isValidIssue\\": true, \\"subCategoryIds\\": [\\"%s\\"], \\"severityScore\\": 4.5, \\"impact\\": 4.0, \\"urgency\\": 4.0, \\"context\\": 4.0, \\"confidenceScore\\": 40.0, \\"description\\": \\"Hàng rào lan can cầu bị tróc sơn\\", \\"priorityReasoning\\": \\"Độ tương đồng thấp với danh mục ổ gà\\"}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """, subSutLun.getSubCategoryId());

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(geminiJson, HttpStatus.OK));

        AiAnalyzeRequest request = new AiAnalyzeRequest();
        request.setMediaUrls(List.of("https://example.com/railing.jpg"));

        AiAnalyzeResponse response = aiService.analyze(request);

        verify(categoryRepository).save(any(Category.class));
        verify(subCategoryRepository).save(any(SubCategory.class));
        assertThat(response.getIsValidIssue()).isTrue();
        assertThat(response.getSubCategoryIds()).containsExactly(subKhac.getSubCategoryId());
        assertThat(response.getSubCategoryNames()).containsExactly("Khác");
        assertThat(response.getConfidenceScore()).isEqualByComparingTo(BigDecimal.valueOf(40.0));
    }

    @Test
    @DisplayName("Khi ảnh không phải sự cố (isValidIssue = false), trả về danh sách SubCategory rỗng")
    void analyze_whenInvalidIssue_returnsEmptySubCategories() {
        when(categoryRepository.findAllByIsDeletedFalse()).thenReturn(List.of(catGiaoThong, catKhac));
        when(subCategoryRepository.findByIsActiveTrueAndIsDeletedFalse()).thenReturn(List.of(subSutLun, subKhac));

        String geminiJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"isValidIssue\\": false, \\"description\\": \\"Ảnh chụp chân dung cá nhân, không phải sự cố cơ sở hạ tầng đô thị.\\"}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(geminiJson, HttpStatus.OK));

        AiAnalyzeRequest request = new AiAnalyzeRequest();
        request.setMediaUrls(List.of("https://example.com/selfie.jpg"));

        AiAnalyzeResponse response = aiService.analyze(request);

        assertThat(response.getIsValidIssue()).isFalse();
        assertThat(response.getSubCategoryIds()).isEmpty();
        assertThat(response.getSubCategoryNames()).isEmpty();
    }
}
