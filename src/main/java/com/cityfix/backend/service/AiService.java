package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.AiAnalyzeRequest;
import com.cityfix.backend.model.dto.response.AiAnalyzeResponse;

import java.util.List;

public interface AiService {
    AiAnalyzeResponse analyzeMedia(String mediaUrl);
    AiAnalyzeResponse analyzeMedia(List<String> mediaUrls);
    AiAnalyzeResponse analyze(AiAnalyzeRequest request);
}
