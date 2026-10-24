package com.cityfix.backend.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin phản hồi sau khi gửi yêu cầu tới quản lý")
public class StaffRequestResponse {

    @Schema(description = "ID thông báo vừa được tạo")
    private UUID notificationId;

    @Schema(description = "ID vé liên quan (nếu có)")
    private UUID ticketId;

    @Schema(description = "ID bước xử lý liên quan (nếu có)")
    private UUID stepId;

    @Schema(description = "Tên bước xử lý liên quan (nếu có)")
    private String stepName;

    @Schema(description = "Tiêu đề thông báo")
    private String title;

    @Schema(description = "Nội dung thông báo")
    private String content;

    @Schema(description = "Danh sách ID của các quản lý nhận được yêu cầu")
    private List<UUID> recipientManagerIds;

    @Schema(description = "Danh sách tên của các quản lý nhận được yêu cầu")
    private List<String> recipientManagerNames;

    @Schema(description = "Thời gian tạo thông báo")
    private ZonedDateTime createdAt;
}
