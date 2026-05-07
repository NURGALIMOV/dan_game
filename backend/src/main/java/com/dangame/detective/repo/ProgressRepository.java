package com.dangame.detective.repo;

import com.dangame.detective.entity.ProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProgressRepository extends JpaRepository<ProgressEntity, Long> {

    Optional<ProgressEntity> findByUserIdAndCaseId(Long userId, String caseId);
}
