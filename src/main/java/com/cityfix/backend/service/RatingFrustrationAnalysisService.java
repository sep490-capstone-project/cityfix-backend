package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.response.RatingFrustrationAnalysisResponse;

import java.util.UUID;

public interface RatingFrustrationAnalysisService {

    RatingFrustrationAnalysisResponse analyzeFrustration(UUID ticketId);

    void checkAndTriggerCrisisAlert(UUID ticketId);
}
