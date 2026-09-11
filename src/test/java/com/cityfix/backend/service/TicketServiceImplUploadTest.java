package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.ImageMapper;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.service.impl.TicketServiceImpl;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplUploadTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @Mock
    private ImageMapper imageMapper;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private MockMultipartFile validFile;

    @BeforeEach
    void setUp() {
        validFile = new MockMultipartFile(
                "file",
                "report_image.jpg",
                "image/jpeg",
                "fake image content".getBytes()
        );
    }

    @Test
    @DisplayName("uploadImage_validFile_success: Upload ảnh thành công và trả về ImageUploadResponse")
    void uploadImage_validFile_success() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);

        Map<String, Object> mockCloudinaryResponse = Map.of(
                "public_id", "cityfix/reports/img_123",
                "secure_url", "https://res.cloudinary.com/cityfix/image/upload/v1/cityfix/reports/img_123.jpg",
                "format", "jpg",
                "width", 1920,
                "height", 1080,
                "bytes", 1024L,
                "created_at", "2026-09-11T12:00:00Z"
        );

        ImageUploadResponse expectedResponse = ImageUploadResponse.builder()
                .publicId("cityfix/reports/img_123")
                .secureUrl("https://res.cloudinary.com/cityfix/image/upload/v1/cityfix/reports/img_123.jpg")
                .format("jpg")
                .width(1920)
                .height(1080)
                .bytes(1024L)
                .createdAt("2026-09-11T12:00:00Z")
                .build();

        when(uploader.upload(any(InputStream.class), any(Map.class))).thenReturn(mockCloudinaryResponse);
        when(imageMapper.toImageUploadResponse(mockCloudinaryResponse)).thenReturn(expectedResponse);

        ImageUploadResponse actualResponse = ticketService.uploadImage(validFile, "cityfix/reports");

        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.getPublicId()).isEqualTo("cityfix/reports/img_123");
        assertThat(actualResponse.getSecureUrl()).contains("cityfix/reports/img_123.jpg");

        verify(uploader, times(1)).upload(any(InputStream.class), any(Map.class));
        verify(imageMapper, times(1)).toImageUploadResponse(mockCloudinaryResponse);
    }

    @Test
    @DisplayName("uploadImage_invalidFormat_throwsAppException: Đuôi file/MIME type không hợp lệ ném INVALID_FILE_FORMAT")
    void uploadImage_invalidFormat_throwsAppException() {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "pdf content".getBytes()
        );

        assertThatThrownBy(() -> ticketService.uploadImage(invalidFile, "cityfix/reports"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_FORMAT);

        verifyNoInteractions(cloudinary);
    }

    @Test
    @DisplayName("uploadImage_exceedsMaxSize_throwsAppException: File lớn hơn 5MB ném FILE_SIZE_EXCEEDED")
    void uploadImage_exceedsMaxSize_throwsAppException() {
        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "huge_photo.png",
                "image/png",
                largeContent
        );

        assertThatThrownBy(() -> ticketService.uploadImage(largeFile, "cityfix/reports"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);

        verifyNoInteractions(cloudinary);
    }

    @Test
    @DisplayName("uploadImage_cloudinaryIOException_throwsAppException: Cloudinary SDK lỗi ném IMAGE_UPLOAD_FAILED")
    void uploadImage_cloudinaryIOException_throwsAppException() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(InputStream.class), any(Map.class))).thenThrow(new IOException("Network error"));

        assertThatThrownBy(() -> ticketService.uploadImage(validFile, "cityfix/reports"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_UPLOAD_FAILED);

        verify(uploader, times(1)).upload(any(InputStream.class), any(Map.class));
    }
}
