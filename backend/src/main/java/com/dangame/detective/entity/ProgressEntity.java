package com.dangame.detective.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "progress")
@Getter
@Setter
@NoArgsConstructor
public class ProgressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "case_id", nullable = false)
    private String caseId;

    @Column(name = "unlocked_docs")
    private String unlockedDocs = "[]";

    @Column(name = "actions_log")
    private String actionsLog = "[]";

    @Column
    private Integer paid = 0;

    @Column
    private Integer completed = 0;

    @Column
    private Integer attempts = 0;

    @Column(name = "started_at", insertable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    public void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}
