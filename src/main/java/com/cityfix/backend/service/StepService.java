package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.StepRequest;
import com.cityfix.backend.model.dto.response.StepResponse;

import java.util.List;
import java.util.UUID;

public interface StepService {
    StepResponse createStep(StepRequest request);
    StepResponse updateStep(UUID id, StepRequest request);
    void deleteStep(UUID id);
    List<StepResponse> getAllSteps();
    List<StepResponse> getStepsBySubCategoryId(UUID subCategoryId);
}
