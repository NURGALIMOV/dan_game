package com.dangame.detective.controller;

import com.dangame.detective.dto.Dtos.AchievementDto;
import com.dangame.detective.dto.Dtos.LeaderEntryDto;
import com.dangame.detective.entity.AchievementEntity;
import com.dangame.detective.entity.UserEntity;
import com.dangame.detective.gamedata.Achievements;
import com.dangame.detective.gamedata.Achievements.Achievement;
import com.dangame.detective.mapper.UserMapper;
import com.dangame.detective.repo.AchievementRepository;
import com.dangame.detective.repo.UserRepository;
import com.dangame.detective.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository users;
    private final AchievementRepository achievements;
    private final UserMapper userMapper;

    @GetMapping("/profile")
    @Transactional(readOnly = true)
    public Map<String, Object> profile(@CurrentUser UserEntity user) {
        // Все достижения пользователя из БД, ключ → дата получения.
        Map<String, LocalDateTime> earnedAt = new LinkedHashMap<>();
        for (AchievementEntity e : achievements.findByUserId(user.getId())) {
            earnedAt.put(e.getAchievementKey(), e.getEarnedAt());
        }

        // Полный список (реестр) с пометкой earned/locked.
        List<AchievementDto> all = Achievements.REGISTRY.values().stream()
            .map((Achievement a) -> {
                LocalDateTime when = earnedAt.get(a.key());
                return new AchievementDto(
                    a.key(), a.title(), a.description(), a.icon(),
                    when != null, when
                );
            })
            .toList();

        long earnedCount = all.stream().filter(AchievementDto::earned).count();

        List<LeaderEntryDto> leaderboard = users.findTop10ByOrderByCasesSolvedDesc().stream()
            .map(userMapper::toLeaderEntry)
            .toList();

        var resp = new LinkedHashMap<String, Object>();
        resp.put("agent_name",         user.getAgentName());
        resp.put("email",              user.getEmail());
        resp.put("email_verified",     Boolean.TRUE.equals(user.getEmailVerified()));
        resp.put("rank",               user.getRank());
        resp.put("cases_solved",       user.getCasesSolved());
        resp.put("achievements",       all);
        resp.put("achievements_earned", earnedCount);
        resp.put("achievements_total", all.size());
        resp.put("leaderboard",        leaderboard);
        return resp;
    }
}
