package com.cityfix.backend.repository.projection;

import java.time.LocalDateTime;
import java.util.UUID;

public interface DuplicateTicketProjection {
    UUID getTicketId();
    String getDescription();
    String getAddress();
    String getStatus();
    String getCategoryName();
    Double getLatitude();
    Double getLongitude();
    Double getDistanceMeters();
    LocalDateTime getCreatedAt();
}
