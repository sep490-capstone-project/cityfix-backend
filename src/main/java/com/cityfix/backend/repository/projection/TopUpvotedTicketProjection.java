package com.cityfix.backend.repository.projection;

import java.util.UUID;

public interface TopUpvotedTicketProjection {
    UUID getTicketId();
    Long getUpvoteCount();
}

