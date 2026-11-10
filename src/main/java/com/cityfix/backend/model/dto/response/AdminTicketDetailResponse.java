package com.cityfix.backend.model.dto.response;

import com.cityfix.backend.model.enums.LocationMethod;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTicketDetailResponse {
    private UUID id;
    private String description;
    private List<TicketCategoryResponse> categories;
    private List<SubCategoryResponse> subCategories;
    private Priority priority;
    private String priorityReasoning;
    private LocationMethod locationMethod;
    private String address;
    private TicketStatus status;
    private Double latitude;
    private Double longitude;
    private String reviewNote;
    private Boolean isAnonymous;
    private Boolean isLifeThreatening;
    private Boolean needsManualReview;
    private UUID createdBy;
    private String creatorFullName;
    private String creatorEmail;
    private String creatorPhoneNumber;
    private List<String> imageUrls;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deadline;
    private UUID departmentId;
    private String departmentName;
    private UUID assigneeId;
    private String assigneeName;
    private long upvoteCount;
    private List<TicketStepResponse> steps;
    private List<TicketHistoryResponse> histories;
    private List<RatingResponse> ratings;
}
