package com.cityfix.backend.model.entity;

import com.cityfix.backend.model.enums.Priority;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "sub_category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "sub_category_id")
    private UUID subCategoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "sla")
    private Integer sla;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private Priority priority;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
