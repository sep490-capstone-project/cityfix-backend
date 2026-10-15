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

import com.cityfix.backend.model.dto.request.UpdateRatingRequest;
import com.cityfix.backend.model.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
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

    @Test
    @DisplayName("getRatings_success")
    void getRatings_success() {
        Pageable pageable = PageRequest.of(0, 10);
        Rating rating = Rating.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .user(currentUser)
                .rate(5)
                .isAnonymous(false)
                .feedback("Dịch vụ tốt")
                .build();
        Page<Rating> ratingPage = new PageImpl<>(List.of(rating), pageable, 1);

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId, pageable)).thenReturn(ratingPage);

        RatingResponse ratingResponse = RatingResponse.builder()
                .id(rating.getId())
                .ticketId(ticketId)
                .userId(currentUser.getUserId())
                .userName(currentUser.getFullName())
                .rate(5)
                .feedback("Dịch vụ tốt")
                .build();
        when(ratingMapper.toResponse(rating)).thenReturn(ratingResponse);

        Page<RatingResponse> result = ratingService.getTicketRatings(ticketId, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getRate()).isEqualTo(5);
        verify(ratingRepository).findByTicket_TicketIdAndIsDeletedFalse(ticketId, pageable);
    }

    @Test
    @DisplayName("getRatings_ticketNotFound_throwsNotFound")
    void getRatings_ticketNotFound_throwsNotFound() {
        Pageable pageable = PageRequest.of(0, 10);
        when(ticketRepository.existsById(ticketId)).thenReturn(false);

        assertThatThrownBy(() -> ratingService.getTicketRatings(ticketId, pageable))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);

        verify(ratingRepository, never()).findByTicket_TicketIdAndIsDeletedFalse(any(), any());
    }

    @Test
    @DisplayName("getMyRating_exists_returnsRating")
    void getMyRating_exists_returnsRating() {
        Rating rating = Rating.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .user(currentUser)
                .rate(4)
                .isAnonymous(false)
                .feedback("Ổn áp")
                .build();

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ratingRepository.findByTicket_TicketIdAndUser_UserIdAndIsDeletedFalse(ticketId, currentUser.getUserId()))
                .thenReturn(Optional.of(rating));

        RatingResponse ratingResponse = RatingResponse.builder()
                .id(rating.getId())
                .ticketId(ticketId)
                .userId(currentUser.getUserId())
                .userName(currentUser.getFullName())
                .rate(4)
                .build();
        when(ratingMapper.toResponse(rating)).thenReturn(ratingResponse);

        RatingResponse result = ratingService.getMyRating(ticketId);

        assertThat(result).isNotNull();
        assertThat(result.getRate()).isEqualTo(4);
        assertThat(result.getUserId()).isEqualTo(currentUser.getUserId());
    }

    @Test
    @DisplayName("getMyRating_notExists_returnsNullOr404")
    void getMyRating_notExists_returnsNullOr404() {
        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ratingRepository.findByTicket_TicketIdAndUser_UserIdAndIsDeletedFalse(ticketId, currentUser.getUserId()))
                .thenReturn(Optional.empty());

        RatingResponse result = ratingService.getMyRating(ticketId);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getMyRating_ticketNotFound_throwsNotFound")
    void getMyRating_ticketNotFound_throwsNotFound() {
        when(ticketRepository.existsById(ticketId)).thenReturn(false);

        assertThatThrownBy(() -> ratingService.getMyRating(ticketId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);
    }

    @Test
    @DisplayName("updateRating_byOwner_success")
    void updateRating_byOwner_success() {
        UUID ratingId = UUID.randomUUID();
        Rating rating = Rating.builder()
                .id(ratingId)
                .ticket(ticket)
                .user(currentUser)
                .rate(3)
                .isAnonymous(false)
                .feedback("Bình thường")
                .build();

        UpdateRatingRequest updateRequest = UpdateRatingRequest.builder()
                .rate(5)
                .isAnonymous(true)
                .feedback("Đã xử lý xong hoàn hảo!")
                .build();

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ratingRepository.findByIdAndTicket_TicketIdAndIsDeletedFalse(ratingId, ticketId))
                .thenReturn(Optional.of(rating));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RatingResponse updatedResponse = RatingResponse.builder()
                .id(ratingId)
                .ticketId(ticketId)
                .rate(5)
                .isAnonymous(true)
                .feedback("Đã xử lý xong hoàn hảo!")
                .build();
        when(ratingMapper.toResponse(any(Rating.class))).thenReturn(updatedResponse);

        RatingResponse result = ratingService.updateRating(ticketId, ratingId, updateRequest);

        assertThat(result).isNotNull();
        assertThat(result.getRate()).isEqualTo(5);
        assertThat(rating.getRate()).isEqualTo(5);
        assertThat(rating.getIsAnonymous()).isTrue();
        assertThat(rating.getFeedback()).isEqualTo("Đã xử lý xong hoàn hảo!");
        assertThat(rating.getUpdatedAt()).isNotNull();
        verify(ratingRepository).save(rating);
    }

    @Test
    @DisplayName("updateRating_byOtherUser_throwsForbidden")
    void updateRating_byOtherUser_throwsForbidden() {
        UUID ratingId = UUID.randomUUID();
        Rating rating = Rating.builder()
                .id(ratingId)
                .ticket(ticket)
                .user(creatorUser) // other user
                .rate(3)
                .build();

        UpdateRatingRequest updateRequest = UpdateRatingRequest.builder()
                .rate(5)
                .build();

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ratingRepository.findByIdAndTicket_TicketIdAndIsDeletedFalse(ratingId, ticketId))
                .thenReturn(Optional.of(rating));

        assertThatThrownBy(() -> ratingService.updateRating(ticketId, ratingId, updateRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(ratingRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateRating_ticketNotFound_throwsNotFound")
    void updateRating_ticketNotFound_throwsNotFound() {
        UUID ratingId = UUID.randomUUID();
        UpdateRatingRequest updateRequest = UpdateRatingRequest.builder().rate(5).build();
        when(ticketRepository.existsById(ticketId)).thenReturn(false);

        assertThatThrownBy(() -> ratingService.updateRating(ticketId, ratingId, updateRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);
    }

    @Test
    @DisplayName("updateRating_ratingNotFound_throwsNotFound")
    void updateRating_ratingNotFound_throwsNotFound() {
        UUID ratingId = UUID.randomUUID();
        UpdateRatingRequest updateRequest = UpdateRatingRequest.builder().rate(5).build();

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ratingRepository.findByIdAndTicket_TicketIdAndIsDeletedFalse(ratingId, ticketId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> ratingService.updateRating(ticketId, ratingId, updateRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATING_NOT_FOUND);

        verify(ratingRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteRating_byOwner_success")
    void deleteRating_byOwner_success() {
        UUID ratingId = UUID.randomUUID();
        Rating rating = Rating.builder()
                .id(ratingId)
                .ticket(ticket)
                .user(currentUser)
                .rate(4)
                .build();

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ratingRepository.findByIdAndTicket_TicketIdAndIsDeletedFalse(ratingId, ticketId))
                .thenReturn(Optional.of(rating));

        ratingService.deleteRating(ticketId, ratingId);

        assertThat(rating.isDeleted()).isTrue();
        assertThat(rating.getDeletedAt()).isNotNull();
        verify(ratingRepository).delete(rating);
    }

    @Test
    @DisplayName("deleteRating_byAdmin_success")
    void deleteRating_byAdmin_success() {
        UUID ratingId = UUID.randomUUID();
        Rating rating = Rating.builder()
                .id(ratingId)
                .ticket(ticket)
                .user(creatorUser) // other user
                .rate(1)
                .build();

        // currentUser has ADMIN role
        currentUser.setRole(Role.builder().roleName("ADMIN").build());

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ratingRepository.findByIdAndTicket_TicketIdAndIsDeletedFalse(ratingId, ticketId))
                .thenReturn(Optional.of(rating));

        ratingService.deleteRating(ticketId, ratingId);

        assertThat(rating.isDeleted()).isTrue();
        assertThat(rating.getDeletedAt()).isNotNull();
        verify(ratingRepository).delete(rating);
    }

    @Test
    @DisplayName("deleteRating_byOtherUser_throwsForbidden")
    void deleteRating_byOtherUser_throwsForbidden() {
        UUID ratingId = UUID.randomUUID();
        Rating rating = Rating.builder()
                .id(ratingId)
                .ticket(ticket)
                .user(creatorUser) // other user
                .rate(1)
                .build();

        // currentUser has CITIZEN role
        currentUser.setRole(Role.builder().roleName("CITIZEN").build());

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ratingRepository.findByIdAndTicket_TicketIdAndIsDeletedFalse(ratingId, ticketId))
                .thenReturn(Optional.of(rating));

        assertThatThrownBy(() -> ratingService.deleteRating(ticketId, ratingId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(ratingRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteRating_ticketNotFound_throwsNotFound")
    void deleteRating_ticketNotFound_throwsNotFound() {
        UUID ratingId = UUID.randomUUID();
        when(ticketRepository.existsById(ticketId)).thenReturn(false);

        assertThatThrownBy(() -> ratingService.deleteRating(ticketId, ratingId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);

        verify(ratingRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteRating_ratingNotFound_throwsNotFound")
    void deleteRating_ratingNotFound_throwsNotFound() {
        UUID ratingId = UUID.randomUUID();
        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ratingRepository.findByIdAndTicket_TicketIdAndIsDeletedFalse(ratingId, ticketId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> ratingService.deleteRating(ticketId, ratingId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATING_NOT_FOUND);

        verify(ratingRepository, never()).delete(any());
    }

    @Test
    @DisplayName("reRateAfterDelete_allowsCreatingNewRating_success")
    void reRateAfterDelete_allowsCreatingNewRating_success() {
        ticket.setUser(currentUser);

        // Step 1: User soft-deletes existing rating
        UUID oldRatingId = UUID.randomUUID();
        Rating oldRating = Rating.builder()
                .id(oldRatingId)
                .ticket(ticket)
                .user(currentUser)
                .rate(2)
                .feedback("Chưa hài lòng")
                .build();

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ratingRepository.findByIdAndTicket_TicketIdAndIsDeletedFalse(oldRatingId, ticketId))
                .thenReturn(Optional.of(oldRating));

        ratingService.deleteRating(ticketId, oldRatingId);

        assertThat(oldRating.isDeleted()).isTrue();
        verify(ratingRepository).delete(oldRating);

        // Step 2: Now user can create a new rating since active rating no longer exists
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ratingRepository.existsByTicket_TicketIdAndUser_UserIdAndIsDeletedFalse(ticketId, currentUser.getUserId()))
                .thenReturn(false);

        Rating newRating = Rating.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .user(currentUser)
                .rate(5)
                .isAnonymous(false)
                .feedback("Đã khắc phục xong, cho 5 sao!")
                .build();
        when(ratingRepository.save(any(Rating.class))).thenReturn(newRating);

        RatingResponse newResponse = RatingResponse.builder()
                .id(newRating.getId())
                .ticketId(ticketId)
                .userId(currentUser.getUserId())
                .userName(currentUser.getFullName())
                .rate(5)
                .feedback("Đã khắc phục xong, cho 5 sao!")
                .build();
        when(ratingMapper.toResponse(newRating)).thenReturn(newResponse);

        CreateRatingRequest newRequest = CreateRatingRequest.builder()
                .rate(5)
                .isAnonymous(false)
                .feedback("Đã khắc phục xong, cho 5 sao!")
                .build();

        RatingResponse createdRating = ratingService.createRating(ticketId, newRequest);

        assertThat(createdRating).isNotNull();
        assertThat(createdRating.getRate()).isEqualTo(5);
        verify(ratingRepository).save(any(Rating.class));
    }
}
