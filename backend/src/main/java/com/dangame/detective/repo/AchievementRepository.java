package com.dangame.detective.repo;

import com.dangame.detective.entity.AchievementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AchievementRepository extends JpaRepository<AchievementEntity, Long> {

    List<AchievementEntity> findByUserId(Long userId);

    @Modifying
    @Query(
        value = "INSERT INTO achievements (user_id, achievement_key) VALUES (:userId, :key) "
              + "ON CONFLICT (user_id, achievement_key) DO NOTHING",
        nativeQuery = true
    )
    void grantIfAbsent(@Param("userId") Long userId, @Param("key") String key);
}
