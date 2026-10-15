package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.CreateRatingRequest;
import com.cityfix.backend.model.dto.request.UpdateRatingRequest;
import com.cityfix.backend.model.dto.response.RatingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RatingService {

    RatingResponse createRating(UUID ticketId, CreateRatingRequest request);

    Page<RatingResponse> getTicketRatings(UUID ticketId, Pageable pageable);

    RatingResponse getMyRating(UUID ticketId);

    RatingResponse updateRating(UUID ticketId, UUID ratingId, UpdateRatingRequest request);

    void deleteRating(UUID ticketId, UUID ratingId);
}
