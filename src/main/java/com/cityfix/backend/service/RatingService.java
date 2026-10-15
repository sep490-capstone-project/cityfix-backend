package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.CreateRatingRequest;
import com.cityfix.backend.model.dto.response.RatingResponse;

import java.util.UUID;

public interface RatingService {
    RatingResponse createRating(UUID ticketId, CreateRatingRequest request);
}
