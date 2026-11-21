package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.response.AiPromptResponse;
import com.cityfix.backend.model.dto.response.AiPromptSummaryResponse;
import com.cityfix.backend.model.entity.AiPromptFile;
import com.cityfix.backend.repository.AiPromptFileRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.lowagie.text.pdf.BaseFont;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiPromptServiceImplTest {

    @Mock
    private AiPromptFileRepository aiPromptFileRepository;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AiPromptServiceImpl aiPromptService;

    @BeforeEach
    void setUp() {
        lenient().when(cloudinary.uploader()).thenReturn(uploader);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("uploadPromptFile_nullOrEmptyFile_throwsInvalidFile")
    void uploadPromptFile_nullOrEmptyFile_throwsInvalidFile() {
        assertThatThrownBy(() -> aiPromptService.uploadPromptFile(null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROMPT_INVALID_FILE);

        MockMultipartFile emptyFile = new MockMultipartFile("file", "prompt.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> aiPromptService.uploadPromptFile(emptyFile))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROMPT_INVALID_FILE);
    }

    @Test
    @DisplayName("uploadPromptFile_nonTxtExtension_throwsInvalidFile")
    void uploadPromptFile_nonTxtExtension_throwsInvalidFile() {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "prompt.pdf", "application/pdf", "Nội dung thử nghiệm".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> aiPromptService.uploadPromptFile(pdfFile))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROMPT_INVALID_FILE);
    }

    @Test
    @DisplayName("uploadPromptFile_whitespaceOnlyContent_throwsInvalidFile")
    void uploadPromptFile_whitespaceOnlyContent_throwsInvalidFile() {
        MockMultipartFile blankFile = new MockMultipartFile(
                "file", "prompt.TXT", "text/plain", "\uFEFF   \r\n\t  ".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> aiPromptService.uploadPromptFile(blankFile))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROMPT_INVALID_FILE);
    }

    @Test
    @DisplayName("uploadPromptFile_exceeds2MB_throwsInvalidFile")
    void uploadPromptFile_exceeds2MB_throwsInvalidFile() {
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        oversized[0] = 'A';
        MockMultipartFile bigFile = new MockMultipartFile("file", "large.txt", "text/plain", oversized);

        assertThatThrownBy(() -> aiPromptService.uploadPromptFile(bigFile))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROMPT_INVALID_FILE);
    }

    @Test
    @DisplayName("convertTextToPdf_longVietnameseTextWithDiacritics_producesValidPdfWithEmbeddedUnicodeFont")
    void convertTextToPdf_longVietnameseTextWithDiacritics_producesValidPdfWithEmbeddedUnicodeFont() throws Exception {
        BaseFont baseFont = aiPromptService.loadVietnameseUnicodeBaseFont();
        for (char c : "ĂÂĐÊÔƠƯăâđêôơưắằẳẵặếềểễệớờởỡợứừửữựỷỹ".toCharArray()) {
            assertThat(baseFont.charExists(c))
                    .as("Embedded font must support Vietnamese character '%s'", c)
                    .isTrue();
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 750; i++) {
            sb.append("Dòng ").append(i)
                    .append(": Hướng dẫn phân loại sự cố hạ tầng đô thị — Ổ gà, cây đổ, ngập úng tại Phường Bến Nghé, TP. Hồ Chí Minh.\n");
        }

        byte[] pdfBytes = aiPromptService.convertTextToPdf(sb.toString());

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(5_000);
        String header = new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("uploadPromptFile_validTxtWithUtf8Bom_stripsBomUploadsRawPdfSavesDbAndEvictsCache")
    @SuppressWarnings("unchecked")
    void uploadPromptFile_validTxtWithUtf8Bom_stripsBomUploadsRawPdfSavesDbAndEvictsCache() throws Exception {
        String rawText = "\uFEFFBạn là trợ lý AI phân tích sự cố đô thị CityFix.\r\n\r\nQuy tắc ưu tiên: Khẩn cấp > Trung bình > Thấp.";
        String expectedCleanText = "Bạn là trợ lý AI phân tích sự cố đô thị CityFix.\r\n\r\nQuy tắc ưu tiên: Khẩn cấp > Trung bình > Thấp.";

        MockMultipartFile txtFile = new MockMultipartFile(
                "file", "cityfix_prompt.txt", "text/plain", rawText.getBytes(StandardCharsets.UTF_8));

        String cloudinaryUrl = "https://res.cloudinary.com/demo/raw/upload/v1/cityfix/ai-prompts/cityfix-ai-prompt-123.pdf";
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", cloudinaryUrl,
                "public_id", "cityfix/ai-prompts/cityfix-ai-prompt-123.pdf"
        ));

        UUID savedId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(aiPromptFileRepository.save(any(AiPromptFile.class))).thenAnswer(inv -> {
            AiPromptFile entity = inv.getArgument(0);
            entity.setId(savedId);
            entity.setCreatedAt(now);
            return entity;
        });

        AiPromptResponse response = aiPromptService.uploadPromptFile(txtFile);

        assertThat(response.getId()).isEqualTo(savedId);
        assertThat(response.getFileUrl()).isEqualTo(cloudinaryUrl);
        assertThat(response.getContent()).isEqualTo(expectedCleanText);
        assertThat(response.getContent()).doesNotStartWith("\uFEFF");
        assertThat(response.getCreatedAt()).isEqualTo(now);

        ArgumentCaptor<byte[]> pdfCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(pdfCaptor.capture(), paramsCaptor.capture());

        assertThat(new String(pdfCaptor.getValue(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        Map<String, Object> uploadParams = paramsCaptor.getValue();
        assertThat(uploadParams.get("resource_type")).isEqualTo("raw");
        assertThat(uploadParams.get("folder")).isEqualTo("cityfix/ai-prompts");
        assertThat(uploadParams.get("public_id").toString()).matches("^cityfix-ai-prompt-\\d+\\.pdf$");

        verify(redisTemplate).delete("ai:prompt:latest");
    }

    @Test
    @DisplayName("uploadPromptFile_dbSaveFails_cleansUpUploadedCloudinaryRawFile")
    void uploadPromptFile_dbSaveFails_cleansUpUploadedCloudinaryRawFile() throws Exception {
        MockMultipartFile txtFile = new MockMultipartFile(
                "file", "prompt.txt", "text/plain", "Prompt tiếng Việt hợp lệ".getBytes(StandardCharsets.UTF_8));

        String uploadedPublicId = "cityfix/ai-prompts/cityfix-ai-prompt-999.pdf";
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/raw/upload/v1/" + uploadedPublicId,
                "public_id", uploadedPublicId
        ));
        when(aiPromptFileRepository.save(any(AiPromptFile.class))).thenThrow(new RuntimeException("DB connection lost"));

        assertThatThrownBy(() -> aiPromptService.uploadPromptFile(txtFile))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB connection lost");

        verify(uploader).destroy(uploadedPublicId, Map.of("resource_type", "raw"));
    }

    @Test
    @DisplayName("getLatestPrompt_recordExists_returnsLatestAndCachesInRedis")
    void getLatestPrompt_recordExists_returnsLatestAndCachesInRedis() {
        UUID id = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        AiPromptFile latest = AiPromptFile.builder()
                .id(id)
                .fileUrl("https://res.cloudinary.com/demo/raw/upload/cityfix/ai-prompts/latest.pdf")
                .content("Nội dung prompt mới nhất")
                .createdAt(createdAt)
                .build();

        when(valueOperations.get("ai:prompt:latest")).thenReturn(null);
        when(aiPromptFileRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(latest));

        AiPromptResponse response = aiPromptService.getLatestPrompt();

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getFileUrl()).isEqualTo(latest.getFileUrl());
        assertThat(response.getContent()).isEqualTo("Nội dung prompt mới nhất");
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
        verify(valueOperations).set(eq("ai:prompt:latest"), anyString(), eq(30L), any());
    }

    @Test
    @DisplayName("getLatestPrompt_emptyTable_throwsAiPromptNotFound")
    void getLatestPrompt_emptyTable_throwsAiPromptNotFound() {
        when(valueOperations.get("ai:prompt:latest")).thenReturn(null);
        when(aiPromptFileRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiPromptService.getLatestPrompt())
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROMPT_NOT_FOUND);
    }

    @Test
    @DisplayName("getPromptHistory_returnsOrderedSummaryListWithoutContent")
    void getPromptHistory_returnsOrderedSummaryListWithoutContent() {
        OffsetDateTime t2 = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime t1 = t2.minusDays(1);

        AiPromptFile newer = AiPromptFile.builder()
                .id(UUID.randomUUID())
                .fileUrl("https://res.cloudinary.com/demo/raw/upload/p2.pdf")
                .content("Nội dung bản 2 rất dài...")
                .createdAt(t2)
                .build();
        AiPromptFile older = AiPromptFile.builder()
                .id(UUID.randomUUID())
                .fileUrl("https://res.cloudinary.com/demo/raw/upload/p1.pdf")
                .content("Nội dung bản 1 rất dài...")
                .createdAt(t1)
                .build();

        when(aiPromptFileRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(newer, older));

        List<AiPromptSummaryResponse> history = aiPromptService.getPromptHistory();

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getId()).isEqualTo(newer.getId());
        assertThat(history.get(0).getFileUrl()).isEqualTo(newer.getFileUrl());
        assertThat(history.get(0).getCreatedAt()).isEqualTo(t2);
        assertThat(history.get(1).getId()).isEqualTo(older.getId());
        assertThat(history.get(1).getCreatedAt()).isEqualTo(t1);
        assertThat(AiPromptSummaryResponse.class.getDeclaredFields())
                .extracting("name")
                .containsExactlyInAnyOrder("id", "fileUrl", "createdAt");
    }
}

