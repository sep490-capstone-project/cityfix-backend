package com.cityfix.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "step")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Step extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "step_id")
    private UUID stepId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_category_id", nullable = false)
    private SubCategory subCategory;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration")
    private Integer duration;
}
