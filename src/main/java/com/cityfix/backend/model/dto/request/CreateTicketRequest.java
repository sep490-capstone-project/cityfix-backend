package com.cityfix.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Range;
import lombok.Data;

@Data
public class CreateTicketRequest {
    
    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Image URL is required")
    private String imageUrl;

    @NotNull(message = "Category ID is required")
    private java.util.UUID categoryId;

    private java.util.List<java.util.UUID> subCategoryIds;

    @NotNull(message = "Severity is required")
    private com.cityfix.backend.model.enums.SeverityLevel severity;

    @NotNull(message = "Priority is required")
    private com.cityfix.backend.model.enums.Priority priority;
    
    @NotNull(message = "Location method is required")
    private com.cityfix.backend.model.enums.LocationMethod locationMethod;
    
    private String address;

    @NotNull(message = "Latitude is required")
    @Range(min = -90, max = 90, message = "Vĩ độ không hợp lệ (-90 đến 90)")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @Range(min = -180, max = 180, message = "Kinh độ không hợp lệ (-180 đến 180)")
    private Double longitude;

    private Double confidenceScore;

    private Boolean isAnonymous;
}
