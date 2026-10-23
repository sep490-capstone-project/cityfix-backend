package com.cityfix.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingFrustrationAnalysisResponse {

    private UUID ticketId;
    private int totalRatings;
    private int oneStarCount;
    private int twoStarCount;
    private int threeStarCount;
    private int fourStarCount;
    private int fiveStarCount;
    private Double averageRating;

    @Builder.Default
    private int penaltyPerOneStar = 2;

    @Builder.Default
    private int penaltyPerTwoStar = 1;

    private int totalPenaltyScore;
    private int totalFollowers;

    private double threshold50;
    private double threshold75;
    private double threshold100;

    private boolean isAlertTriggered;
    private String currentMilestone;
    private String analysisSummary;
}
