package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.UpdateStaffTicketStatusRequest;
import com.cityfix.backend.model.dto.response.ResolutionEvidenceResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.enums.TicketStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface StaffTicketService {

    TicketResponse updateTicketStatus(UUID ticketId, TicketStatus targetStatus, UpdateStaffTicketStatusRequest request);

    List<ResolutionEvidenceResponse> submitEvidence(UUID stepId, List<MultipartFile> files, String resolutionNote);
}
