package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.service.AiService;
import com.cityfix.backend.service.TicketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketControllerUploadTest {

    @Mock
    private TicketService ticketService;

    @Mock
    private AiService aiService;

    @InjectMocks
    private TicketController ticketController;

    @Test
    @DisplayName("uploadImage_validMultipartFile_returns200WithData: Upload ảnh qua controller thành công trả về 200 OK và BaseResponse chuẩn")
    void uploadImage_validMultipartFile_returns200WithData() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        ImageUploadResponse mockResponse = ImageUploadResponse.builder()
                .publicId("cityfix/reports/img_123")
                .secureUrl("https://res.cloudinary.com/cityfix/image/upload/v1/cityfix/reports/img_123.jpg")
                .format("jpg")
                .width(1920)
                .height(1080)
                .bytes(1024L)
                .createdAt("2026-09-11T12:00:00Z")
                .build();

        when(ticketService.uploadImage(any(), eq("cityfix/reports"))).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<ImageUploadResponse>> responseEntity = ticketController.uploadImage(file, "cityfix/reports");

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().isSuccess()).isTrue();
        assertThat(responseEntity.getBody().getMessage()).isEqualTo("Tải ảnh lên thành công.");
        assertThat(responseEntity.getBody().getData()).isEqualTo(mockResponse);

        verify(ticketService, times(1)).uploadImage(file, "cityfix/reports");
    }
}
