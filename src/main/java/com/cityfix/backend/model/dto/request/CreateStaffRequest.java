package com.cityfix.backend.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu từ nhân viên gửi đến người quản lý phòng ban")
public class CreateStaffRequest {

    @Schema(description = "ID của sự cố liên quan (tùy chọn)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID ticketId;

    @Schema(description = "ID của bước xử lý cụ thể trong ticket_step (tùy chọn)", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID stepId;

    @NotBlank(message = "Tiêu đề yêu cầu không được để trống")
    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    @Schema(description = "Tiêu đề yêu cầu", example = "Đề xuất gia hạn thời gian xử lý sự cố")
    private String title;

    @NotBlank(message = "Nội dung yêu cầu không được để trống")
    @Schema(description = "Nội dung chi tiết của yêu cầu", example = "Do địa hình ngập úng nghiêm trọng cần xin thêm thiết bị hỗ trợ.")
    private String content;
}
