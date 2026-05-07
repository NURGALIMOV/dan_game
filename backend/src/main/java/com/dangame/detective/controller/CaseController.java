package com.dangame.detective.controller;

import com.dangame.detective.ApiException;
import com.dangame.detective.Ranks;
import com.dangame.detective.dto.Dtos.Accuse;
import com.dangame.detective.dto.Dtos.CaseStart;
import com.dangame.detective.dto.Dtos.DoAction;
import com.dangame.detective.dto.Dtos.OpenDoc;
import com.dangame.detective.entity.ProgressEntity;
import com.dangame.detective.entity.UserEntity;
import com.dangame.detective.gamedata.Cases;
import com.dangame.detective.gamedata.Cases.Action;
import com.dangame.detective.gamedata.Cases.Case;
import com.dangame.detective.gamedata.Cases.Doc;
import com.dangame.detective.repo.AchievementRepository;
import com.dangame.detective.repo.ProgressRepository;
import com.dangame.detective.repo.UserRepository;
import com.dangame.detective.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CaseController {

    private static final String DEFAULT_CASE_ID = "case_001";

    private final ProgressRepository progresses;
    private final AchievementRepository achievements;
    private final UserRepository users;
    private final ObjectMapper json;

    @GetMapping("/cases")
    public List<Map<String, String>> listCases() {
        return Cases.CASES.values().stream()
            .map(c -> Map.of("id", c.id(), "title", c.title(), "subtitle", c.subtitle()))
            .toList();
    }

    @PostMapping("/case/start")
    @Transactional
    public Map<String, Object> start(
        @CurrentUser UserEntity user,
        @RequestBody(required = false) CaseStart req
    ) {
        String caseId = (req == null || req.caseId() == null) ? DEFAULT_CASE_ID : req.caseId();
        Case cs = caseOrThrow(caseId);

        ProgressEntity prog = progresses.findByUserIdAndCaseId(user.getId(), caseId)
            .orElseGet(() -> {
                ProgressEntity fresh = new ProgressEntity();
                fresh.setUserId(user.getId());
                fresh.setCaseId(caseId);
                return progresses.save(fresh);
            });

        boolean paid = prog.getPaid() == 1;
        List<Doc> docs = new ArrayList<>(cs.demoDocs());
        if (paid) docs.addAll(cs.paidDocs());
        List<String> unlocked = parseStringList(prog.getUnlockedDocs());
        List<Action> actions = filterActions(cs, unlocked);

        var caseInfo = new LinkedHashMap<String, Object>();
        caseInfo.put("id",               cs.id());
        caseInfo.put("title",            cs.title());
        caseInfo.put("victim",           cs.victim());
        caseInfo.put("location",         cs.location());
        caseInfo.put("time",             cs.time());
        caseInfo.put("official_version", cs.officialVersion());
        caseInfo.put("suspects",         cs.suspects());
        caseInfo.put("price",            cs.price());

        var resp = new LinkedHashMap<String, Object>();
        resp.put("case",          caseInfo);
        resp.put("docs",          docs);
        resp.put("actions",       actions);
        resp.put("paid",          paid);
        resp.put("completed",     prog.getCompleted() == 1);
        resp.put("unlocked_docs", unlocked);
        return resp;
    }

    @PostMapping("/case/open_doc")
    @Transactional
    public Map<String, Object> openDoc(
        @CurrentUser UserEntity user,
        @Valid @RequestBody OpenDoc req
    ) {
        Case cs = caseOrThrow(req.caseId());
        ProgressEntity prog = progresses.findByUserIdAndCaseId(user.getId(), req.caseId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Прогресс не найден"));

        List<String> unlocked = new ArrayList<>(parseStringList(prog.getUnlockedDocs()));
        if (!unlocked.contains(req.docId())) {
            unlocked.add(req.docId());
            List<Map<String, Object>> log = new ArrayList<>(parseLog(prog.getActionsLog()));
            log.add(Map.of(
                "action", "opened:" + req.docId(),
                "time",   LocalDateTime.now().toString()
            ));
            prog.setUnlockedDocs(writeJson(unlocked));
            prog.setActionsLog(writeJson(log));
        }

        List<Action> newActions = filterActions(cs, unlocked);
        var resp = new LinkedHashMap<String, Object>();
        resp.put("unlocked_docs",     unlocked);
        resp.put("available_actions", newActions);
        return resp;
    }

    @PostMapping("/case/action")
    @Transactional
    public Map<String, String> action(
        @CurrentUser UserEntity user,
        @Valid @RequestBody DoAction req
    ) {
        Case cs = caseOrThrow(req.caseId());
        Action act = cs.assistantActions().stream()
            .filter(a -> a.id().equals(req.action()))
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Действие не найдено"));

        progresses.findByUserIdAndCaseId(user.getId(), req.caseId()).ifPresent(p -> {
            List<Map<String, Object>> log = new ArrayList<>(parseLog(p.getActionsLog()));
            log.add(Map.of(
                "action", req.action(),
                "time",   LocalDateTime.now().toString()
            ));
            p.setActionsLog(writeJson(log));
        });
        return Map.of("response", act.response());
    }

    @PostMapping("/case/accuse")
    @Transactional
    public Map<String, Object> accuse(
        @CurrentUser UserEntity user,
        @Valid @RequestBody Accuse req
    ) {
        Case cs = caseOrThrow(req.caseId());

        Optional<ProgressEntity> progOpt = progresses.findByUserIdAndCaseId(user.getId(), req.caseId());
        boolean isCorrect = req.suspect().equals(cs.answer());
        boolean paid = progOpt.map(p -> p.getPaid() == 1).orElse(false);

        if (!paid) {
            return Map.of(
                "result",  "demo_end",
                "message", "Демо-версия завершена. Чтобы узнать правильный ответ и получить доступ к полному расследованию — оплатите полную версию.",
                "price",   cs.price()
            );
        }

        ProgressEntity prog = progOpt.get();
        int attempts = prog.getAttempts() + 1;
        prog.setAttempts(attempts);

        if (isCorrect) {
            int displaySolved = user.getCasesSolved() + 1;
            if (prog.getCompleted() == 0) {
                prog.setCompleted(1);
                user.setCasesSolved(displaySolved);
                user.setRank(Ranks.calculate(displaySolved));
                users.save(user);
                if (attempts == 1) {
                    achievements.grantIfAbsent(user.getId(), "intuition");
                }
            }
            return Map.of(
                "result",  "correct",
                "message", "Верно. Убийца — Марина Волкова. 18 лет она ждала этого момента.",
                "rank",    Ranks.calculate(displaySolved)
            );
        }
        return Map.of(
            "result",  "wrong",
            "message", "Неверно. Ты обвинил " + req.suspect() + ". Пересмотри материалы — что-то ты упустил."
        );
    }

    private static Case caseOrThrow(String caseId) {
        Case cs = Cases.CASES.get(caseId);
        if (cs == null) throw new ApiException(HttpStatus.NOT_FOUND, "Дело не найдено");
        return cs;
    }

    private static List<Action> filterActions(Case cs, List<String> unlocked) {
        return cs.assistantActions().stream()
            .filter(a -> a.requiresDoc() == null || unlocked.contains(a.requiresDoc()))
            .toList();
    }

    private List<String> parseStringList(String raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        try {
            return json.readValue(raw, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> parseLog(String raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        try {
            return json.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка сериализации JSON");
        }
    }
}
