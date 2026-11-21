package com.cityfix.backend.repository;

import com.cityfix.backend.model.entity.AiPromptFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiPromptFileRepository extends JpaRepository<AiPromptFile, UUID> {

    Optional<AiPromptFile> findTopByOrderByCreatedAtDescIdDesc();

    List<AiPromptFile> findAllByOrderByCreatedAtDescIdDesc();
}

