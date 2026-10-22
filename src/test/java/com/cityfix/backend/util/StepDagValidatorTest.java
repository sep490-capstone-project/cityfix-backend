package com.cityfix.backend.util;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepDagValidatorTest {

    @Test
    @DisplayName("validateDag_validChain_passesValidation")
    void validateDag_validChain_passesValidation() {
        UUID step1 = UUID.randomUUID();
        UUID step2 = UUID.randomUUID();
        UUID step3 = UUID.randomUUID();

        List<UUID> allStepIds = List.of(step1, step2, step3);
        Map<UUID, Set<UUID>> adjacencyMap = new HashMap<>();
        adjacencyMap.put(step1, Collections.emptySet()); // Anchor
        adjacencyMap.put(step2, Set.of(step1));
        adjacencyMap.put(step3, Set.of(step2));

        assertThatCode(() -> StepDagValidator.validateDag(allStepIds, adjacencyMap))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateDag_validFanOutAndFanIn_passesValidation")
    void validateDag_validFanOutAndFanIn_passesValidation() {
        UUID stepA = UUID.randomUUID();
        UUID stepB = UUID.randomUUID();
        UUID stepC = UUID.randomUUID();
        UUID stepD = UUID.randomUUID();

        // A is anchor, B and C depend on A (fan-out), D depends on B and C (fan-in)
        List<UUID> allStepIds = List.of(stepA, stepB, stepC, stepD);
        Map<UUID, Set<UUID>> adjacencyMap = new HashMap<>();
        adjacencyMap.put(stepA, Collections.emptySet());
        adjacencyMap.put(stepB, Set.of(stepA));
        adjacencyMap.put(stepC, Set.of(stepA));
        adjacencyMap.put(stepD, Set.of(stepB, stepC));

        assertThatCode(() -> StepDagValidator.validateDag(allStepIds, adjacencyMap))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateDag_noAnchorStep_throwsAnchorStepRequired")
    void validateDag_noAnchorStep_throwsAnchorStepRequired() {
        UUID step1 = UUID.randomUUID();
        UUID step2 = UUID.randomUUID();

        // step1 depends on step2, step2 depends on step1 -> no anchor step
        List<UUID> allStepIds = List.of(step1, step2);
        Map<UUID, Set<UUID>> adjacencyMap = new HashMap<>();
        adjacencyMap.put(step1, Set.of(step2));
        adjacencyMap.put(step2, Set.of(step1));

        assertThatThrownBy(() -> StepDagValidator.validateDag(allStepIds, adjacencyMap))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANCHOR_STEP_REQUIRED);
    }

    @Test
    @DisplayName("validateDag_cycleDetected_throwsStepCycleDetected")
    void validateDag_cycleDetected_throwsStepCycleDetected() {
        UUID stepA = UUID.randomUUID();
        UUID stepB = UUID.randomUUID();
        UUID stepC = UUID.randomUUID();
        UUID stepD = UUID.randomUUID();

        // stepA is anchor. Cycle between B -> C -> D -> B
        List<UUID> allStepIds = List.of(stepA, stepB, stepC, stepD);
        Map<UUID, Set<UUID>> adjacencyMap = new HashMap<>();
        adjacencyMap.put(stepA, Collections.emptySet()); // Anchor exists
        adjacencyMap.put(stepB, Set.of(stepA, stepD));
        adjacencyMap.put(stepC, Set.of(stepB));
        adjacencyMap.put(stepD, Set.of(stepC));

        assertThatThrownBy(() -> StepDagValidator.validateDag(allStepIds, adjacencyMap))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.STEP_CYCLE_DETECTED);
    }

    @Test
    @DisplayName("validateDag_selfDependency_throwsInvalidStepDependency")
    void validateDag_selfDependency_throwsInvalidStepDependency() {
        UUID step1 = UUID.randomUUID();

        List<UUID> allStepIds = List.of(step1);
        Map<UUID, Set<UUID>> adjacencyMap = new HashMap<>();
        adjacencyMap.put(step1, Set.of(step1));

        assertThatThrownBy(() -> StepDagValidator.validateDag(allStepIds, adjacencyMap))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STEP_DEPENDENCY);
    }

    @Test
    @DisplayName("validateDag_crossTicketDependency_throwsInvalidStepDependency")
    void validateDag_crossTicketDependency_throwsInvalidStepDependency() {
        UUID step1 = UUID.randomUUID();
        UUID stepOutside = UUID.randomUUID();

        List<UUID> allStepIds = List.of(step1);
        Map<UUID, Set<UUID>> adjacencyMap = new HashMap<>();
        adjacencyMap.put(step1, Set.of(stepOutside));

        assertThatThrownBy(() -> StepDagValidator.validateDag(allStepIds, adjacencyMap))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STEP_DEPENDENCY);
    }
}
