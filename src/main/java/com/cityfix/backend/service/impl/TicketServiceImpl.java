package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.request.CreateTicketRequest;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.entity.ReportImage;
import com.cityfix.backend.model.entity.TicketVote;
import com.cityfix.backend.model.entity.AiAnalysis;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.CategoryRepository;
import com.cityfix.backend.repository.ReportImageRepository;
import com.cityfix.backend.repository.TicketVoteRepository;
import com.cityfix.backend.repository.AiAnalysisRepository;
import com.cityfix.backend.repository.TicketRepository;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketVoteRepository ticketVoteRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ReportImageRepository reportImageRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    
    // WGS84 SRID is 4326
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Override
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
        }

        Point location = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));

        Ticket ticket = Ticket.builder()
                .description(request.getDescription())
                .category(category)
                .severity(request.getSeverity())
                .dangerLevel(request.getDangerLevel())
                .locationMethod(request.getLocationMethod())
                .address(request.getAddress())
                .status(TicketStatus.INITIATED)
                .location(location)
                .user(user)
                .build();

        ticket = ticketRepository.save(ticket);

        if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
            ReportImage image = ReportImage.builder()
                    .ticket(ticket)
                    .imageUrl(request.getImageUrl())
                    .build();
            reportImageRepository.save(image);
        }

        if (request.getAiConfidenceScore() != null) {
            Category aiCategory = null;
            if (request.getAiCategoryId() != null) {
                aiCategory = categoryRepository.findById(request.getAiCategoryId()).orElse(null);
            }

            AiAnalysis analysis = AiAnalysis.builder()
                    .ticket(ticket)
                    .category(aiCategory)
                    .severity(request.getAiSeverity())
                    .dangerLevel(request.getAiDangerLevel())
                    .description(request.getAiDescription())
                    .confidenceScore(request.getAiConfidenceScore())
                    .isAcceptedByHuman(request.getIsAiAccepted() != null ? request.getIsAiAccepted() : false)
                    .build();
            aiAnalysisRepository.save(analysis);
        }

        return mapToResponse(ticket);
    }

    @Override
    @Transactional
    public void upvoteTicket(UUID ticketId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        if (ticketVoteRepository.existsByTicket_TicketIdAndUser_UserId(ticketId, user.getUserId())) {
            throw new RuntimeException("User has already upvoted this ticket");
        }

        TicketVote vote = TicketVote.builder()
                .ticket(ticket)
                .user(user)
                .build();
        
        ticketVoteRepository.save(vote);
    }

    private TicketResponse mapToResponse(Ticket ticket) {
        Double lat = null;
        Double lon = null;
        if (ticket.getLocation() != null) {
            lat = ticket.getLocation().getY();
            lon = ticket.getLocation().getX();
        }

        return TicketResponse.builder()
                .id(ticket.getTicketId())
                .description(ticket.getDescription())
                .categoryId(ticket.getCategory() != null ? ticket.getCategory().getCategoryId() : null)
                .severity(ticket.getSeverity())
                .dangerLevel(ticket.getDangerLevel())
                .locationMethod(ticket.getLocationMethod())
                .address(ticket.getAddress())
                .status(ticket.getStatus())
                .latitude(lat)
                .longitude(lon)
                .createdBy(ticket.getUser().getUserId())
                .createdAt(ticket.getCreatedAt())
                .build();
    }
}
