package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.response.AiAnalyzeResponse;

public interface AiService {
    AiAnalyzeResponse analyzeImage(String imageUrl);
}
