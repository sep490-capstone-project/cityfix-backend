package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.RatingResponse;
import com.cityfix.backend.model.entity.Rating;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RatingMapperTest {

    private RatingMapper ratingMapper;

    private User user;
    private Ticket ticket;
    private UUID ratingId;
    private UUID ticketId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        ratingMapper = Mappers.getMapper(RatingMapper.class);

        ratingId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        userId = UUID.randomUUID();

        user = User.builder()
                .userId(userId)
                .fullName("Nguyễn Văn A")
                .email("citizen@cityfix.com")
                .build();

        ticket = Ticket.builder()
                .ticketId(ticketId)
                .build();
    }

    @Test
    @DisplayName("toResponse_nonAnonymous_preservesUserInfo")
    void toResponse_nonAnonymous_preservesUserInfo() {
        Rating rating = Rating.builder()
                .id(ratingId)
                .ticket(ticket)
                .user(user)
                .rate(5)
                .isAnonymous(false)
                .feedback("Rất hài lòng")
                .build();
        rating.setCreatedAt(LocalDateTime.now());

        RatingResponse response = ratingMapper.toResponse(rating);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(ratingId);
        assertThat(response.getTicketId()).isEqualTo(ticketId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getUserName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.getRate()).isEqualTo(5);
        assertThat(response.getIsAnonymous()).isFalse();
        assertThat(response.getFeedback()).isEqualTo("Rất hài lòng");
    }

    @Test
    @DisplayName("toResponse_anonymous_masksUserInfo")
    void toResponse_anonymous_masksUserInfo() {
        Rating rating = Rating.builder()
                .id(ratingId)
                .ticket(ticket)
                .user(user)
                .rate(4)
                .isAnonymous(true)
                .feedback("Xử lý ổn")
                .build();
        rating.setCreatedAt(LocalDateTime.now());

        RatingResponse response = ratingMapper.toResponse(rating);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(ratingId);
        assertThat(response.getTicketId()).isEqualTo(ticketId);
        assertThat(response.getUserId()).isNull();
        assertThat(response.getUserName()).isNull();
        assertThat(response.getRate()).isEqualTo(4);
        assertThat(response.getIsAnonymous()).isTrue();
        assertThat(response.getFeedback()).isEqualTo("Xử lý ổn");
    }
}
