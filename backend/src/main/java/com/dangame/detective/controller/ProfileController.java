package com.dangame.detective.controller;

import com.dangame.detective.dto.Dtos.AchievementDto;
import com.dangame.detective.dto.Dtos.LeaderEntryDto;
import com.dangame.detective.entity.UserEntity;
import com.dangame.detective.mapper.AchievementMapper;
import com.dangame.detective.mapper.UserMapper;
import com.dangame.detective.repo.AchievementRepository;
import com.dangame.detective.repo.UserRepository;
import com.dangame.detective.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final AchievementMapper achievementMapper;

    @GetMapping("/profile")
    @Transactional(readOnly = true)
    public Map<String, Object> profile(@CurrentUser UserEntity user) {
        List<AchievementDto> userAchievements = achievements.findByUserId(user.getId()).stream()
            .map(achievementMapper::toDto)
            .toList();

        List<LeaderEntryDto> leaderboard = users.findTop10ByOrderByCasesSolvedDesc().stream()
            .map(userMapper::toLeaderEntry)
            .toList();

        var resp = new LinkedHashMap<String, Object>();
        resp.put("agent_name",   user.getAgentName());
        resp.put("email",        user.getEmail());
        resp.put("rank",         user.getRank());
        resp.put("cases_solved", user.getCasesSolved());
        resp.put("achievements", userAchievements);
        resp.put("leaderboard",  leaderboard);
        return resp;
    }
}
