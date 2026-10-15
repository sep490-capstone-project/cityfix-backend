package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.RatingMapper;
import com.cityfix.backend.model.dto.request.CreateRatingRequest;
import com.cityfix.backend.model.dto.response.RatingResponse;
import com.cityfix.backend.model.entity.Rating;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.RatingRepository;
import com.cityfix.backend.repository.TicketRepository;
import com.cityfix.backend.repository.TicketVoteRepository;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.impl.RatingServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingServiceImplTest {

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketVoteRepository ticketVoteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RatingMapper ratingMapper;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private RatingServiceImpl ratingService;

    private User currentUser;
    private User creatorUser;
    private Ticket ticket;
    private UUID ticketId;
    private CreateRatingRequest request;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();

        currentUser = new User();
        currentUser.setUserId(UUID.randomUUID());
        currentUser.setEmail("citizen@cityfix.com");
        currentUser.setFullName("Nguyễn Văn A");

        creatorUser = new User();
        creatorUser.setUserId(UUID.randomUUID());
        creatorUser.setEmail("creator@cityfix.com");
        creatorUser.setFullName("Trần Văn B");

        ticket = Ticket.builder()
                .ticketId(ticketId)
                .user(creatorUser)
                .status(TicketStatus.RESOLVED)
                .build();

        request = CreateRatingRequest.builder()
                .rate(5)
                .isAnonymous(false)
                .feedback("Xử lý rất nhanh và sạch sẽ!")
                .build();

        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getPrincipal()).thenReturn(currentUser);
        lenient().when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(currentUser));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createRating_byCreator_success")
    void createRating_byCreator_success() {
        ticket.setUser(currentUser);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ratingRepository.existsByTicket_TicketIdAndUser_UserIdAndIsDeletedFalse(ticketId, currentUser.getUserId())).thenReturn(false);

        Rating savedRating = Rating.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .user(currentUser)
                .rate(5)
                .isAnonymous(false)
                .feedback("Tuyệt vời")
                .build();

        when(ratingRepository.save(any(Rating.class))).thenReturn(savedRating);

        RatingResponse expectedResponse = RatingResponse.builder()
                .id(savedRating.getId())
                .ticketId(ticketId)
                .userId(currentUser.getUserId())
                .userName(currentUser.getFullName())
                .rate(5)
                .isAnonymous(false)
                .feedback("Tuyệt vời")
                .build();

        when(ratingMapper.toResponse(savedRating)).thenReturn(expectedResponse);

        RatingResponse response = ratingService.createRating(ticketId, request);

        assertThat(response).isNotNull();
        assertThat(response.getRate()).isEqualTo(5);
        assertThat(response.getUserId()).isEqualTo(currentUser.getUserId());
        assertThat(response.getUserName()).isEqualTo("Nguyễn Văn A");
        verify(ratingRepository, times(1)).save(any(Rating.class));
    }

    @Test
    @DisplayName("createRating_byFollower_success")
    void createRating_byFollower_success() {
        ticket.setUser(creatorUser);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketVoteRepository.existsByTicket_TicketIdAndUser_UserId(ticketId, currentUser.getUserId())).thenReturn(true);
        when(ratingRepository.existsByTicket_TicketIdAndUser_UserIdAndIsDeletedFalse(ticketId, currentUser.getUserId())).thenReturn(false);

        Rating savedRating = Rating.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .user(currentUser)
                .rate(4)
                .isAnonymous(false)
                .build();

        when(ratingRepository.save(any(Rating.class))).thenReturn(savedRating);

        RatingResponse expectedResponse = RatingResponse.builder()
                .id(savedRating.getId())
                .ticketId(ticketId)
                .userId(currentUser.getUserId())
                .userName(currentUser.getFullName())
                .rate(4)
                .isAnonymous(false)
                .build();

        when(ratingMapper.toResponse(savedRating)).thenReturn(expectedResponse);

        RatingResponse response = ratingService.createRating(ticketId, request);

        assertThat(response).isNotNull();
        assertThat(response.getRate()).isEqualTo(4);
        verify(ratingRepository, times(1)).save(any(Rating.class));
    }

    @Test
    @DisplayName("createRating_anonymous_masksUserInfo")
    void createRating_anonymous_masksUserInfo() {
        ticket.setUser(currentUser);
        request.setIsAnonymous(true);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ratingRepository.existsByTicket_TicketIdAndUser_UserIdAndIsDeletedFalse(ticketId, currentUser.getUserId())).thenReturn(false);

        Rating savedRating = Rating.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .user(currentUser)
                .rate(5)
                .isAnonymous(true)
                .build();

        when(ratingRepository.save(any(Rating.class))).thenReturn(savedRating);

        RatingResponse expectedResponse = RatingResponse.builder()
                .id(savedRating.getId())
                .ticketId(ticketId)
                .userId(null)
                .userName(null)
                .rate(5)
                .isAnonymous(true)
                .build();

        when(ratingMapper.toResponse(savedRating)).thenReturn(expectedResponse);

        RatingResponse response = ratingService.createRating(ticketId, request);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isNull();
        assertThat(response.getUserName()).isNull();
        assertThat(response.getIsAnonymous()).isTrue();
    }

    @Test
    @DisplayName("createRating_whenPreviousRatingSoftDeleted_allowsNewRating")
    void createRating_whenPreviousRatingSoftDeleted_allowsNewRating() {
        ticket.setUser(currentUser);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        // Rating cũ đã bị soft-delete (isDeleted = true), nên method trả về false
        when(ratingRepository.existsByTicket_TicketIdAndUser_UserIdAndIsDeletedFalse(ticketId, currentUser.getUserId())).thenReturn(false);

        Rating newRating = Rating.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .user(currentUser)
                .rate(5)
                .isAnonymous(false)
                .feedback("Đánh giá lại sau khi khiếu nại")
                .build();

        when(ratingRepository.save(any(Rating.class))).thenReturn(newRating);

        RatingResponse expectedResponse = RatingResponse.builder()
                .id(newRating.getId())
                .ticketId(ticketId)
                .userId(currentUser.getUserId())
                .userName(currentUser.getFullName())
                .rate(5)
                .isAnonymous(false)
                .feedback("Đánh giá lại sau khi khiếu nại")
                .build();

        when(ratingMapper.toResponse(newRating)).thenReturn(expectedResponse);

        RatingResponse response = ratingService.createRating(ticketId, request);

        assertThat(response).isNotNull();
        assertThat(response.getRate()).isEqualTo(5);
        verify(ratingRepository, times(1)).save(any(Rating.class));
    }

    @Test
    @DisplayName("createRating_ticketNotFound_throwsNotFound")
    void createRating_ticketNotFound_throwsNotFound() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ratingService.createRating(ticketId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);

        verify(ratingRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRating_ticketNotCompleted_throwsBadRequest")
    void createRating_ticketNotCompleted_throwsBadRequest() {
        ticket.setStatus(TicketStatus.IN_PROGRESS);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ratingService.createRating(ticketId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_NOT_COMPLETED);

        verify(ratingRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRating_notPermitted_throwsForbidden")
    void createRating_notPermitted_throwsForbidden() {
        ticket.setUser(creatorUser);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketVoteRepository.existsByTicket_TicketIdAndUser_UserId(ticketId, currentUser.getUserId())).thenReturn(false);

        assertThatThrownBy(() -> ratingService.createRating(ticketId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_NOT_PERMITTED_TO_RATE);

        verify(ratingRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRating_alreadyRated_throwsConflict")
    void createRating_alreadyRated_throwsConflict() {
        ticket.setUser(currentUser);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ratingRepository.existsByTicket_TicketIdAndUser_UserIdAndIsDeletedFalse(ticketId, currentUser.getUserId())).thenReturn(true);

        assertThatThrownBy(() -> ratingService.createRating(ticketId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATING_ALREADY_EXISTS);

        verify(ratingRepository, never()).save(any());
    }
}
