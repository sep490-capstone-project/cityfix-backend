package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.response.AiPromptResponse;
import com.cityfix.backend.model.dto.response.AiPromptSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AiPromptService {

    AiPromptResponse uploadPromptFile(MultipartFile file);

    AiPromptResponse getLatestPrompt();

    List<AiPromptSummaryResponse> getPromptHistory();
}

