package com.cityfix.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "category_id")
    private UUID categoryId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "sla")
    private Integer sla;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
