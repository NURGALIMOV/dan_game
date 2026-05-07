package com.dangame.detective.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "achievements",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_achievements_user_key",
        columnNames = {"user_id", "achievement_key"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class AchievementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "achievement_key", nullable = false)
    private String achievementKey;

    @Column(name = "earned_at", insertable = false, updatable = false)
    private LocalDateTime earnedAt;
}
