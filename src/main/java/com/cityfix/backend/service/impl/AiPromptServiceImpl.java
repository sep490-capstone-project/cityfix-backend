package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.response.AiPromptResponse;
import com.cityfix.backend.model.dto.response.AiPromptSummaryResponse;
import com.cityfix.backend.model.entity.AiPromptFile;
import com.cityfix.backend.repository.AiPromptFileRepository;
import com.cityfix.backend.service.AiPromptService;
import com.cloudinary.Cloudinary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPromptServiceImpl implements AiPromptService {

    private static final long MAX_TXT_FILE_SIZE_BYTES = 2L * 1024 * 1024; // 2MB
    private static final String CLOUDINARY_FOLDER = "cityfix/ai-prompts";
    private static final String LATEST_PROMPT_CACHE_KEY = "ai:prompt:latest";
    private static final long LATEST_PROMPT_CACHE_TTL_MINUTES = 30L;
    private static final String UNICODE_FONT_CLASSPATH = "liberation/LiberationSans-Regular.ttf";

    private final AiPromptFileRepository aiPromptFileRepository;
    private final Cloudinary cloudinary;
    private final RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private volatile byte[] cachedFontBytes;

    @Override
    @Transactional
    public AiPromptResponse uploadPromptFile(MultipartFile file) {
        String content = validateAndReadTxtContent(file);
        byte[] pdfBytes = convertTextToPdf(content);

        String publicIdFilename = "cityfix-ai-prompt-" + System.currentTimeMillis() + ".pdf";
        String uploadedPublicId = CLOUDINARY_FOLDER + "/" + publicIdFilename;
        String secureUrl;

        try {
            Map<String, Object> uploadParams = Map.of(
                    "resource_type", "raw",
                    "folder", CLOUDINARY_FOLDER,
                    "public_id", publicIdFilename
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader().upload(pdfBytes, uploadParams);
            if (uploadResult == null || uploadResult.get("secure_url") == null) {
                throw new AppException(ErrorCode.AI_PROMPT_UPLOAD_FAILED);
            }
            secureUrl = uploadResult.get("secure_url").toString();
            if (uploadResult.get("public_id") != null) {
                uploadedPublicId = uploadResult.get("public_id").toString();
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi upload tệp AI Prompt PDF lên Cloudinary: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.AI_PROMPT_UPLOAD_FAILED);
        }

        try {
            AiPromptFile entity = AiPromptFile.builder()
                    .fileUrl(secureUrl)
                    .content(content)
                    .build();
            AiPromptFile saved = aiPromptFileRepository.save(entity);
            evictLatestPromptCache();
            return toResponse(saved);
        } catch (Exception dbEx) {
            cleanupCloudinaryRawFile(uploadedPublicId);
            throw dbEx;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AiPromptResponse getLatestPrompt() {
        AiPromptResponse cached = getLatestPromptFromCache();
        if (cached != null) {
            return cached;
        }

        AiPromptFile latest = aiPromptFileRepository.findTopByOrderByCreatedAtDesc()
                .orElseThrow(() -> new AppException(ErrorCode.AI_PROMPT_NOT_FOUND));

        AiPromptResponse response = toResponse(latest);
        putLatestPromptToCache(response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiPromptSummaryResponse> getPromptHistory() {
        return aiPromptFileRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    public byte[] convertTextToPdf(String content) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BaseFont baseFont = loadVietnameseUnicodeBaseFont();
            Font bodyFont = new Font(baseFont, 10.5f, Font.NORMAL);

            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            String[] lines = content.split("\\R", -1);
            for (String line : lines) {
                Paragraph paragraph = new Paragraph(line.isEmpty() ? " " : line, bodyFont);
                paragraph.setLeading(14f);
                document.add(paragraph);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Lỗi khi chuyển đổi nội dung AI Prompt sang PDF: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.AI_PROMPT_PDF_CONVERSION_FAILED);
        }
    }

    BaseFont loadVietnameseUnicodeBaseFont() throws IOException {
        byte[] fontBytes = cachedFontBytes;
        if (fontBytes == null) {
            synchronized (this) {
                fontBytes = cachedFontBytes;
                if (fontBytes == null) {
                    ClassPathResource fontResource = new ClassPathResource(UNICODE_FONT_CLASSPATH);
                    try (InputStream is = fontResource.getInputStream()) {
                        fontBytes = is.readAllBytes();
                        cachedFontBytes = fontBytes;
                    }
                }
            }
        }
        return BaseFont.createFont(
                "LiberationSans-Regular.ttf",
                BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED,
                true,
                fontBytes,
                null
        );
    }

    private String validateAndReadTxtContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.AI_PROMPT_INVALID_FILE);
        }

        if (file.getSize() > MAX_TXT_FILE_SIZE_BYTES) {
            throw new AppException(ErrorCode.AI_PROMPT_INVALID_FILE, "Dung lượng tệp AI Prompt vượt quá giới hạn tối đa 2MB.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.trim().toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw new AppException(ErrorCode.AI_PROMPT_INVALID_FILE, "Chỉ chấp nhận tệp định dạng .txt.");
        }

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Lỗi khi đọc nội dung tệp AI Prompt: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.AI_PROMPT_INVALID_FILE);
        }

        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        if (content.isBlank()) {
            throw new AppException(ErrorCode.AI_PROMPT_INVALID_FILE, "Nội dung tệp AI Prompt không được để trống.");
        }

        return content;
    }

    private void cleanupCloudinaryRawFile(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, Map.of("resource_type", "raw"));
            log.info("Đã dọn tệp rác trên Cloudinary sau khi lưu DB thất bại: {}", publicId);
        } catch (Exception cleanupEx) {
            log.error("Không thể dọn tệp rác trên Cloudinary ({}): {}", publicId, cleanupEx.getMessage(), cleanupEx);
        }
    }

    private AiPromptResponse getLatestPromptFromCache() {
        try {
            if (redisTemplate == null || redisTemplate.opsForValue() == null) {
                return null;
            }
            String json = redisTemplate.opsForValue().get(LATEST_PROMPT_CACHE_KEY);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, AiPromptResponse.class);
        } catch (Exception e) {
            log.warn("Không thể đọc cache Redis cho AI Prompt mới nhất: {}", e.getMessage());
            return null;
        }
    }

    private void putLatestPromptToCache(AiPromptResponse response) {
        try {
            if (redisTemplate == null || redisTemplate.opsForValue() == null) {
                return;
            }
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(LATEST_PROMPT_CACHE_KEY, json, LATEST_PROMPT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Không thể ghi cache Redis cho AI Prompt mới nhất: {}", e.getMessage());
        }
    }

    private void evictLatestPromptCache() {
        try {
            if (redisTemplate != null) {
                redisTemplate.delete(LATEST_PROMPT_CACHE_KEY);
            }
        } catch (Exception e) {
            log.warn("Không thể xóa cache Redis cho AI Prompt mới nhất: {}", e.getMessage());
        }
    }

    private AiPromptResponse toResponse(AiPromptFile entity) {
        return AiPromptResponse.builder()
                .id(entity.getId())
                .fileUrl(entity.getFileUrl())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private AiPromptSummaryResponse toSummaryResponse(AiPromptFile entity) {
        return AiPromptSummaryResponse.builder()
                .id(entity.getId())
                .fileUrl(entity.getFileUrl())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

