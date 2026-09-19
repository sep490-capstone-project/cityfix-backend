package com.cityfix.backend.model.entity;

import com.cityfix.backend.model.enums.DepartmentLevel;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "department_id")
    private UUID departmentId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepartmentLevel level;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @OneToMany(mappedBy = "department")
    private List<User> officers;
}
