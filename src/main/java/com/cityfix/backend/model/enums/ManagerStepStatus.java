package com.cityfix.backend.model.enums;

public enum ManagerStepStatus {
    IN_PROGRESS,
    RESOLVED,
    COMPLETED;

    public AssignmentStatus toAssignmentStatus() {
        return AssignmentStatus.valueOf(this.name());
    }
}
