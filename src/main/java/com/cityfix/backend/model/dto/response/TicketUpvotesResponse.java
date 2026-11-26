package com.cityfix.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketUpvotesResponse {
    private UUID ticketId;
    private long upvoteCount;
    private List<TicketUpvoterResponse> upvoters;
}
