package com.cityfix.backend.model.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalyzeRequest {

    /**
     * Danh sách URL hình ảnh/video cần phân tích (cho phép gửi 1 link hoặc nhiều link ảnh)
     */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    @JsonAlias("mediaUrl")
    @Schema(
            description = "Danh sách URL ảnh/video để AI phân tích (cho phép gửi 1 link dạng chuỗi hoặc mảng nhiều link)",
            example = "[\"https://res.cloudinary.com/demo/image/upload/sample.jpg\"]"
    )
    @NotEmpty(message = "Danh sách Media URL không được để trống")
    private List<String> mediaUrls;

    /**
     * Gom và làm sạch tất cả các URL hợp lệ (loại bỏ null, khoảng trắng thừa bằng trim())
     */
    public List<String> extractAllMediaUrls() {
        if (mediaUrls == null || mediaUrls.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (String url : mediaUrls) {
            if (url != null) {
                String trimmed = url.trim();
                if (!trimmed.isEmpty()) {
                    list.add(trimmed);
                }
            }
        }
        return list;
    }

    @JsonIgnore
    @AssertTrue(message = "Ít nhất một Media URL hợp lệ là bắt buộc")
    public boolean isValidMediaProvided() {
        return !extractAllMediaUrls().isEmpty();
    }
}
