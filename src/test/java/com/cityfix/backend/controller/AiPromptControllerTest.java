package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.response.AiPromptResponse;
import com.cityfix.backend.model.dto.response.AiPromptSummaryResponse;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.service.AiPromptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiPromptControllerTest {

    @Mock
    private AiPromptService aiPromptService;

    @InjectMocks
    private AiPromptController aiPromptController;

    @Test
    @DisplayName("uploadPromptFile_returns201CreatedWithPromptResponse")
    void uploadPromptFile_returns201CreatedWithPromptResponse() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "prompt.txt", "text/plain", "Prompt mẫu".getBytes(StandardCharsets.UTF_8));

        AiPromptResponse mockResponse = AiPromptResponse.builder()
                .id(UUID.randomUUID())
                .fileUrl("https://res.cloudinary.com/demo/raw/upload/cityfix/ai-prompts/cityfix-ai-prompt-1.pdf")
                .content("Prompt mẫu")
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        when(aiPromptService.uploadPromptFile(file)).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<AiPromptResponse>> response = aiPromptController.uploadPromptFile(file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(mockResponse);
        verify(aiPromptService, times(1)).uploadPromptFile(file);
    }

    @Test
    @DisplayName("getLatestPrompt_returns200OkWithFullPromptResponse")
    void getLatestPrompt_returns200OkWithFullPromptResponse() {
        AiPromptResponse mockResponse = AiPromptResponse.builder()
                .id(UUID.randomUUID())
                .fileUrl("https://res.cloudinary.com/demo/raw/upload/cityfix/ai-prompts/latest.pdf")
                .content("Nội dung prompt mới nhất")
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        when(aiPromptService.getLatestPrompt()).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<AiPromptResponse>> response = aiPromptController.getLatestPrompt();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(mockResponse);
        verify(aiPromptService, times(1)).getLatestPrompt();
    }

    @Test
    @DisplayName("getPromptHistory_returns200OkWithSummaryList")
    void getPromptHistory_returns200OkWithSummaryList() {
        AiPromptSummaryResponse summary = AiPromptSummaryResponse.builder()
                .id(UUID.randomUUID())
                .fileUrl("https://res.cloudinary.com/demo/raw/upload/cityfix/ai-prompts/v1.pdf")
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        when(aiPromptService.getPromptHistory()).thenReturn(List.of(summary));

        ResponseEntity<BaseResponse<List<AiPromptSummaryResponse>>> response = aiPromptController.getPromptHistory();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).containsExactly(summary);
        verify(aiPromptService, times(1)).getPromptHistory();
    }

    @Test
    @DisplayName("controllerSecurityAndAppendOnlyContract_enforcesAdminVsAuthenticatedAndNoUpdateDelete")
    void controllerSecurityAndAppendOnlyContract_enforcesAdminVsAuthenticatedAndNoUpdateDelete() throws Exception {
        Method uploadMethod = AiPromptController.class.getMethod("uploadPromptFile", MultipartFile.class);
        Method latestMethod = AiPromptController.class.getMethod("getLatestPrompt");
        Method historyMethod = AiPromptController.class.getMethod("getPromptHistory");

        assertThat(uploadMethod.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(historyMethod.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(latestMethod.getAnnotation(PreAuthorize.class).value()).isEqualTo("isAuthenticated()");

        for (Method m : AiPromptController.class.getDeclaredMethods()) {
            assertThat(m.getAnnotation(PutMapping.class)).isNull();
            assertThat(m.getAnnotation(PatchMapping.class)).isNull();
            assertThat(m.getAnnotation(DeleteMapping.class)).isNull();
        }
    }
}

