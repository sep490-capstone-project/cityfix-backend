package com.cityfix.backend.service.impl;

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
import com.cityfix.backend.service.RatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cityfix.backend.model.dto.request.UpdateRatingRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {

    private final RatingRepository ratingRepository;
    private final TicketRepository ticketRepository;
    private final TicketVoteRepository ticketVoteRepository;
    private final UserRepository userRepository;
    private final RatingMapper ratingMapper;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String email;
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            email = user.getEmail();
        } else if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            email = principal.toString();
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    @Override
    @Transactional
    public RatingResponse createRating(UUID ticketId, CreateRatingRequest request) {
        User currentUser = getCurrentUser();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.COMPLETED) {
            throw new AppException(ErrorCode.TICKET_NOT_COMPLETED);
        }

        boolean isCreator = ticket.getUser() != null && ticket.getUser().getUserId().equals(currentUser.getUserId());
        boolean isFollower = ticketVoteRepository.existsByTicket_TicketIdAndUser_UserId(ticketId, currentUser.getUserId());

        if (!isCreator && !isFollower) {
            throw new AppException(ErrorCode.TICKET_NOT_PERMITTED_TO_RATE);
        }

        if (ratingRepository.existsByTicket_TicketIdAndUser_UserIdAndIsDeletedFalse(ticketId, currentUser.getUserId())) {
            throw new AppException(ErrorCode.RATING_ALREADY_EXISTS);
        }

        Rating rating = Rating.builder()
                .ticket(ticket)
                .user(currentUser)
                .rate(request.getRate())
                .isAnonymous(Boolean.TRUE.equals(request.getIsAnonymous()))
                .feedback(request.getFeedback())
                .build();

        rating = ratingRepository.save(rating);

        return ratingMapper.toResponse(rating);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RatingResponse> getTicketRatings(UUID ticketId, Pageable pageable) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new AppException(ErrorCode.TICKET_NOT_FOUND);
        }

        Page<Rating> ratings = ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId, pageable);
        return ratings.map(ratingMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public RatingResponse getMyRating(UUID ticketId) {
        User currentUser = getCurrentUser();

        if (!ticketRepository.existsById(ticketId)) {
            throw new AppException(ErrorCode.TICKET_NOT_FOUND);
        }

        return ratingRepository.findByTicket_TicketIdAndUser_UserIdAndIsDeletedFalse(ticketId, currentUser.getUserId())
                .map(ratingMapper::toResponse)
                .orElse(null);
    }

    @Override
    @Transactional
    public RatingResponse updateRating(UUID ticketId, UUID ratingId, UpdateRatingRequest request) {
        User currentUser = getCurrentUser();

        if (!ticketRepository.existsById(ticketId)) {
            throw new AppException(ErrorCode.TICKET_NOT_FOUND);
        }

        Rating rating = ratingRepository.findByIdAndTicket_TicketIdAndIsDeletedFalse(ratingId, ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.RATING_NOT_FOUND));

        if (rating.getUser() == null || !rating.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        rating.setRate(request.getRate());
        if (request.getIsAnonymous() != null) {
            rating.setIsAnonymous(request.getIsAnonymous());
        }
        rating.setFeedback(request.getFeedback());
        rating.setUpdatedAt(LocalDateTime.now());

        rating = ratingRepository.save(rating);
        return ratingMapper.toResponse(rating);
    }

    @Override
    @Transactional
    public void deleteRating(UUID ticketId, UUID ratingId) {
        User currentUser = getCurrentUser();

        if (!ticketRepository.existsById(ticketId)) {
            throw new AppException(ErrorCode.TICKET_NOT_FOUND);
        }

        Rating rating = ratingRepository.findByIdAndTicket_TicketIdAndIsDeletedFalse(ratingId, ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.RATING_NOT_FOUND));

        boolean isOwner = rating.getUser() != null && rating.getUser().getUserId().equals(currentUser.getUserId());
        boolean isAdmin = currentUser.getRole() != null && "ADMIN".equalsIgnoreCase(currentUser.getRole().getRoleName());

        if (!isOwner && !isAdmin) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        rating.setDeleted(true);
        rating.setDeletedAt(LocalDateTime.now());
        ratingRepository.delete(rating);
    }
}
