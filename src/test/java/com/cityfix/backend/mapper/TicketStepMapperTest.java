package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.model.entity.Step;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.AssignmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TicketStepMapperTest {

    @Mock
    private EvidenceMapper evidenceMapper;

    @InjectMocks
    private TicketStepMapperImpl ticketStepMapper;

    @Test
    @DisplayName("toTicketStepResponse_mapsDependsOnToIdsCorrectly")
    void toTicketStepResponse_mapsDependsOnToIdsCorrectly() {
        UUID step1Id = UUID.randomUUID();
        UUID step2Id = UUID.randomUUID();
        UUID currentStepId = UUID.randomUUID();

        TicketStep dep1 = TicketStep.builder().id(step1Id).build();
        TicketStep dep2 = TicketStep.builder().id(step2Id).build();

        Step stepDef = Step.builder().stepId(UUID.randomUUID()).stepName("Bước khảo sát").build();
        User assignee = User.builder().userId(UUID.randomUUID()).fullName("Nguyễn Văn Kỹ Thuật").build();

        TicketStep currentStep = TicketStep.builder()
                .id(currentStepId)
                .step(stepDef)
                .assignee(assignee)
                .status(AssignmentStatus.ASSIGNED)
                .dependsOn(Set.of(dep1, dep2))
                .build();

        TicketStepResponse response = ticketStepMapper.toTicketStepResponse(currentStep);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(currentStepId);
        assertThat(response.getStepName()).isEqualTo("Bước khảo sát");
        assertThat(response.getAssigneeName()).isEqualTo("Nguyễn Văn Kỹ Thuật");
        assertThat(response.getDependsOnStepIds()).containsExactlyInAnyOrder(step1Id, step2Id);
    }

    @Test
    @DisplayName("toTicketStepResponse_emptyDependsOn_returnsEmptyList")
    void toTicketStepResponse_emptyDependsOn_returnsEmptyList() {
        TicketStep currentStep = TicketStep.builder()
                .id(UUID.randomUUID())
                .status(AssignmentStatus.ASSIGNED)
                .dependsOn(Collections.emptySet())
                .build();

        TicketStepResponse response = ticketStepMapper.toTicketStepResponse(currentStep);

        assertThat(response).isNotNull();
        assertThat(response.getDependsOnStepIds()).isNotNull().isEmpty();
    }
}
