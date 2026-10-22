package com.cityfix.backend.model.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalyzeRequest {

    /**
     * URL hình ảnh/video đơn lẻ (hỗ trợ tương thích ngược)
     */
    @Schema(description = "URL của 1 ảnh (nếu chỉ gửi 1 ảnh)")
    private String mediaUrl;

    /**
     * Danh sách 1 - 5 URLs hình ảnh/video cho việc phân tích đa góc nhìn
     */
    @Schema(description = "Danh sách từ 1 đến 5 URL ảnh/video để AI phân tích đa góc nhìn")
    private List<String> mediaUrls;

    /**
     * Gom tất cả các URL hợp lệ từ mediaUrls hoặc mediaUrl
     */
    public List<String> extractAllMediaUrls() {
        List<String> list = new ArrayList<>();
        if (mediaUrls != null && !mediaUrls.isEmpty()) {
            for (String url : mediaUrls) {
                if (url != null && !url.trim().isEmpty()) {
                    list.add(url.trim());
                }
            }
        } else if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {
            list.add(mediaUrl.trim());
        }
        return list;
    }

    @JsonIgnore
    @AssertTrue(message = "Ít nhất một Media URL (mediaUrl hoặc mediaUrls) là bắt buộc")
    public boolean isValidMediaProvided() {
        return !extractAllMediaUrls().isEmpty();
    }
}
