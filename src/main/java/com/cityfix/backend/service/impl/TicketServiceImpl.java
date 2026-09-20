package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.ImageMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.model.dto.request.CreateTicketRequest;
import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.repository.projection.DuplicateTicketProjection;

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
import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketVoteRepository ticketVoteRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ReportImageRepository reportImageRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final Cloudinary cloudinary;
    private final ImageMapper imageMapper;
    private final TicketMapper ticketMapper;


    private static final List<String> ALLOWED_IMAGE_MIME_TYPES = List.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/heic",
            "image/heif"
    );

    private static final List<String> ALLOWED_VIDEO_MIME_TYPES = List.of(
            "video/mp4",
            "video/quicktime",
            "video/webm",
            "video/x-msvideo",
            "video/3gpp"
    );

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_VIDEO_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int MAX_FILE_COUNT = 7;

    private static final java.util.concurrent.Executor uploadExecutor = java.util.concurrent.Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors() * 2, 8)
    );

    private static final double DUPLICATE_SEARCH_RADIUS_METERS = 50.0;

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
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
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
                .isLifeThreatening(false)
                .needsManualReview(false)
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

        TicketResponse response = ticketMapper.toTicketResponse(ticket);
        if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
            response.setImageUrl(request.getImageUrl());
        }
        return response;
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

    @Override
    public List<ImageUploadResponse> uploadImage(List<MultipartFile> files, String folder) {
        if (files == null || files.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT);
        }

        if (files.size() > MAX_FILE_COUNT) {
            throw new AppException(ErrorCode.FILE_COUNT_EXCEEDED);
        }

        for (MultipartFile file : files) {
            validateFile(file);
        }

        String targetFolder = (folder != null && !folder.isBlank()) ? folder : "cityfix/reports";

        List<java.util.concurrent.CompletableFuture<ImageUploadResponse>> futures = files.stream()
                .map(file -> java.util.concurrent.CompletableFuture.supplyAsync(() -> uploadSingleFile(file, targetFolder), uploadExecutor))
                .toList();

        try {
            java.util.concurrent.CompletableFuture<Void> allOf = java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]));
            allOf.join();
            return futures.stream().map(java.util.concurrent.CompletableFuture::join).toList();
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AppException appException) {
                throw appException;
            }
            log.error("Lỗi khi upload tệp lên Cloudinary: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT);
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT);
        }

        String lowerContentType = contentType.toLowerCase();
        boolean isImage = ALLOWED_IMAGE_MIME_TYPES.contains(lowerContentType);
        boolean isVideo = ALLOWED_VIDEO_MIME_TYPES.contains(lowerContentType);

        if (!isImage && !isVideo) {
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT);
        }

        if (isImage && file.getSize() > MAX_IMAGE_SIZE) {
            throw new AppException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        if (isVideo && file.getSize() > MAX_VIDEO_SIZE) {
            throw new AppException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    private ImageUploadResponse uploadSingleFile(MultipartFile file, String targetFolder) {
        try {
            Map<String, Object> uploadParams = Map.of(
                    "folder", targetFolder,
                    "resource_type", "auto",
                    "unique_filename", true
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);
            return imageMapper.toImageUploadResponse(uploadResult);
        } catch (IOException e) {
            log.error("Lỗi khi upload tệp lên Cloudinary: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DuplicateTicketResponse> findDuplicateTickets(Double latitude, Double longitude, UUID categoryId) {
        if (latitude == null || longitude == null || categoryId == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        List<DuplicateTicketProjection> projections = ticketRepository.findDuplicateTickets(latitude, longitude, categoryId);
        return ticketMapper.toDuplicateTicketResponseList(projections);
    }
}


