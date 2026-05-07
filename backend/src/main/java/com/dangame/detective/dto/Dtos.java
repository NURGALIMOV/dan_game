package com.dangame.detective.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class Dtos {

    // ─── REQUESTS ────────────────────────────────────────────────────────

    public record Register(
        @NotBlank(message = "Email обязателен")
        @Email(message = "Некорректный email")
        String email,

        @NotBlank(message = "Пароль обязателен")
        @Size(min = 6, message = "Пароль должен быть минимум 6 символов")
        String password,

        @NotBlank(message = "Позывной обязателен")
        String agentName
    ) {}

    public record Login(
        @NotBlank(message = "Email обязателен")
        String email,

        @NotBlank(message = "Пароль обязателен")
        String password
    ) {}

    public record CaseStart(String caseId) {}

    public record OpenDoc(
        @NotBlank(message = "case_id обязателен") String caseId,
        @NotBlank(message = "doc_id обязателен")  String docId
    ) {}

    public record DoAction(
        @NotBlank(message = "case_id обязателен") String caseId,
        @NotBlank(message = "action обязателен")  String action
    ) {}

    public record Accuse(
        @NotBlank(message = "case_id обязателен") String caseId,
        @NotBlank(message = "suspect обязателен") String suspect
    ) {}

    public record Payment(
        @NotBlank(message = "case_id обязателен") String caseId
    ) {}

    // ─── RESPONSES ───────────────────────────────────────────────────────

    public record AuthResponse(
        String token,
        String agentName,
        String rank
    ) {}

    public record AchievementDto(
        String        achievementKey,
        LocalDateTime earnedAt
    ) {}

    public record LeaderEntryDto(
        String  agentName,
        String  rank,
        Integer casesSolved
    ) {}

    private Dtos() {}
}
