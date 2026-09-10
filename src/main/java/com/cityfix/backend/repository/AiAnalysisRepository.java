package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, UUID> {
}
