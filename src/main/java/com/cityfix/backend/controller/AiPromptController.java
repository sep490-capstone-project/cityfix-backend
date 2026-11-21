package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.response.AiPromptResponse;
import com.cityfix.backend.model.dto.response.AiPromptSummaryResponse;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.service.AiPromptService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai-prompts")
@RequiredArgsConstructor
public class AiPromptController {

    private final AiPromptService aiPromptService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Tải lên tệp AI Prompt mới (.txt -> PDF -> Cloudinary)",
            description = "Chỉ dành cho ADMIN. Nhận tệp .txt UTF-8, chuyển đổi sang PDF tiếng Việt, tải lên Cloudinary và lưu lịch sử dạng Append-Only."
    )
    public ResponseEntity<BaseResponse<AiPromptResponse>> uploadPromptFile(
            @RequestParam("file") MultipartFile file) {
        AiPromptResponse response = aiPromptService.uploadPromptFile(file);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.success("Tải lên tệp AI Prompt thành công.", response));
    }

    @GetMapping("/latest")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Lấy phiên bản AI Prompt mới nhất",
            description = "Trả về đầy đủ thông tin và nội dung (content) của phiên bản AI Prompt mới nhất theo createdAt DESC."
    )
    public ResponseEntity<BaseResponse<AiPromptResponse>> getLatestPrompt() {
        AiPromptResponse response = aiPromptService.getLatestPrompt();
        return ResponseEntity.ok(BaseResponse.success("Lấy AI Prompt mới nhất thành công.", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Lấy toàn bộ lịch sử phiên bản AI Prompt",
            description = "Chỉ dành cho ADMIN. Trả về danh sách tóm tắt (không bao gồm content) sắp xếp theo createdAt DESC."
    )
    public ResponseEntity<BaseResponse<List<AiPromptSummaryResponse>>> getPromptHistory() {
        List<AiPromptSummaryResponse> response = aiPromptService.getPromptHistory();
        return ResponseEntity.ok(BaseResponse.success("Lấy danh sách lịch sử AI Prompt thành công.", response));
    }
}

