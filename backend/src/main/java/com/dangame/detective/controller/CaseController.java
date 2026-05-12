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
import com.dangame.detective.gamedata.Cases.Epilogue;
import com.dangame.detective.gamedata.Cases.EpilogueClue;
import com.dangame.detective.gamedata.Cases.Evidence;
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
import org.springframework.web.bind.annotation.PathVariable;
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
        List<Evidence> evidences = filterEvidences(cs, unlocked);

        var caseInfo = new LinkedHashMap<String, Object>();
        caseInfo.put("id",                            cs.id());
        caseInfo.put("title",                         cs.title());
        caseInfo.put("victim",                        cs.victim());
        caseInfo.put("location",                      cs.location());
        caseInfo.put("time",                          cs.time());
        caseInfo.put("official_version",              cs.officialVersion());
        caseInfo.put("suspects",                      cs.suspects());
        caseInfo.put("price",                         cs.price());
        caseInfo.put("min_evidences_for_accusation", cs.minEvidencesForAccusation());

        var resp = new LinkedHashMap<String, Object>();
        resp.put("case",                 caseInfo);
        resp.put("docs",                 docs);
        resp.put("actions",              actions);
        resp.put("available_evidences",  evidences);
        resp.put("required_doc_count",   docs.size());
        resp.put("paid",                 paid);
        resp.put("completed",            prog.getCompleted() == 1);
        resp.put("unlocked_docs",        unlocked);
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

            // Достижение «Дотошный» — все документы дела открыты.
            int totalDocs = cs.demoDocs().size() + cs.paidDocs().size();
            if (unlocked.size() >= totalDocs) {
                achievements.grantIfAbsent(user.getId(), "marathoner");
            }
        }

        List<Action> newActions = filterActions(cs, unlocked);
        List<Evidence> newEvidences = filterEvidences(cs, unlocked);
        var resp = new LinkedHashMap<String, Object>();
        resp.put("unlocked_docs",       unlocked);
        resp.put("available_actions",   newActions);
        resp.put("available_evidences", newEvidences);
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
        boolean paid = progOpt.map(p -> p.getPaid() == 1).orElse(false);

        // 1. Демо-режим: paywall до любых проверок (как было).
        if (!paid) {
            return Map.of(
                "result",  "demo_end",
                "message", "Демо-версия завершена. Чтобы узнать правильный ответ и получить доступ к полному расследованию — оплатите полную версию.",
                "price",   cs.price()
            );
        }

        ProgressEntity prog = progOpt.get();
        List<String> unlocked = parseStringList(prog.getUnlockedDocs());

        // 2. Все документы дела должны быть открыты.
        List<String> requiredDocIds = new ArrayList<>();
        cs.demoDocs().forEach(d -> requiredDocIds.add(d.id()));
        cs.paidDocs().forEach(d -> requiredDocIds.add(d.id()));
        List<String> missingDocs = requiredDocIds.stream()
            .filter(id -> !unlocked.contains(id))
            .toList();
        if (!missingDocs.isEmpty()) {
            // Маркер ранней попытки обвинения — нужен для достижения «Самоконтроль».
            appendLog(prog, "early_accuse_attempt");
            return Map.of(
                "result",       "needs_investigation",
                "message",      "Нельзя выдвигать обвинение, не изучив все материалы дела. Открой все документы — их " + requiredDocIds.size() + ".",
                "missing_docs", missingDocs
            );
        }

        // 3. Должны быть выбраны улики.
        List<String> selected = req.selectedEvidences() == null ? List.of() : req.selectedEvidences();
        int minEv = cs.minEvidencesForAccusation();
        if (selected.size() < minEv) {
            return Map.of(
                "result",       "needs_evidence",
                "message",      "Подбери минимум " + minEv + " улик(и), указывающих на подозреваемого. Обвинение должно опираться на доказательства.",
                "min_required", minEv
            );
        }

        // 4. Все выбранные улики должны существовать и быть доступны (источник открыт).
        Map<String, Evidence> byId = new LinkedHashMap<>();
        cs.evidences().forEach(e -> byId.put(e.id(), e));
        for (String evId : selected) {
            Evidence ev = byId.get(evId);
            if (ev == null || !unlocked.contains(ev.sourceDoc())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Недопустимая улика: " + evId);
            }
        }

        // 5. Анализ выбранных улик: указывают ли они все на обвиняемого.
        long pointsToSuspect = selected.stream()
            .map(byId::get)
            .filter(e -> req.suspect().equals(e.pointsTo()))
            .count();
        boolean hasContradicting = selected.stream()
            .map(byId::get)
            .anyMatch(e -> e.pointsTo() != null && !req.suspect().equals(e.pointsTo()));

        if (hasContradicting) {
            achievements.grantIfAbsent(user.getId(), "false_lead");
            // Подсказка: найдём конкретный противоречащий фрагмент и сошлёмся на него.
            Evidence problem = selected.stream()
                .map(byId::get)
                .filter(e -> e.pointsTo() != null && !req.suspect().equals(e.pointsTo()))
                .findFirst()
                .orElse(null);
            String hint = problem == null ? "" :
                "Соколов: «Фрагмент ›" + problem.title() +
                "‹ указывает скорее на " + problem.pointsTo() + ", а не на " + req.suspect() + ". Подумай ещё.»";
            return Map.of(
                "result",  "wrong_evidence",
                "message", "Среди выбранных фрагментов есть указывающие на другого человека. Логика обвинения противоречива.",
                "hint",    hint
            );
        }
        if (pointsToSuspect < minEv) {
            // Подсказка: какая-то выбранная улика нейтральна — её одну упомянем.
            Evidence neutral = selected.stream()
                .map(byId::get)
                .filter(e -> e.pointsTo() == null)
                .findFirst()
                .orElse(null);
            String hint = neutral == null
                ? "Соколов: «Этих фрагментов недостаточно, чтобы выйти на конкретное имя. Перечитай материалы.»"
                : "Соколов: «›" + neutral.title() + "‹ — это контекст дела, а не доказательство против конкретного человека. Ищи что-то более определённое.»";
            return Map.of(
                "result",  "wrong_evidence",
                "message", "Выбранные фрагменты не складываются в обвинение. Нужно минимум " + minEv + " прямо указывающих на " + req.suspect() + ".",
                "hint",    hint
            );
        }

        // 6. Улики корректны — теперь смотрим, правильный ли подозреваемый.
        int attempts = prog.getAttempts() + 1;
        prog.setAttempts(attempts);

        boolean isCorrect = req.suspect().equals(cs.answer());
        if (isCorrect) {
            int displaySolved = user.getCasesSolved() + 1;
            if (prog.getCompleted() == 0) {
                prog.setCompleted(1);
                user.setCasesSolved(displaySolved);
                user.setRank(Ranks.calculate(displaySolved));
                users.save(user);

                // «Дело закрыто» — за факт раскрытия любым числом попыток.
                achievements.grantIfAbsent(user.getId(), "case_solved");

                // «Интуиция» — раскрыл с первой попытки обвинения.
                if (attempts == 1) {
                    achievements.grantIfAbsent(user.getId(), "intuition");
                }

                // «Самоконтроль» — ни разу не пытался обвинить до изучения всех документов.
                if (!hasLogEntry(prog, "early_accuse_attempt")) {
                    achievements.grantIfAbsent(user.getId(), "careful");
                }

                // «Аналитик» — выбраны ВСЕ улики, указывающие на убийцу.
                long totalPointingToAnswer = cs.evidences().stream()
                    .filter(e -> cs.answer().equals(e.pointsTo()))
                    .count();
                if (totalPointingToAnswer > 0 && pointsToSuspect == totalPointingToAnswer) {
                    achievements.grantIfAbsent(user.getId(), "evidence_master");
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
            "message", "Фрагменты действительно складываются вокруг " + req.suspect() + "... но это не он. Что-то крупное ты упустил.",
            "hint",    "Соколов: «" + req.suspect() + " — фигурант, но не убийца. Посмотри на тех, кто не на виду. У кого был доступ к Краснову каждый день, годами?»"
        );
    }

    @GetMapping("/case/{caseId}/epilogue")
    @Transactional(readOnly = true)
    public Map<String, Object> epilogue(
        @CurrentUser UserEntity user,
        @PathVariable String caseId
    ) {
        Case cs = caseOrThrow(caseId);
        ProgressEntity prog = progresses.findByUserIdAndCaseId(user.getId(), caseId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Прогресс не найден"));

        if (prog.getCompleted() != 1) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Эпилог открывается только после раскрытия дела.");
        }

        Epilogue ep = cs.epilogue();
        if (ep == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Эпилог для этого дела не подготовлен.");
        }

        // Обогащаем clueBreakdown полным текстом улики из реестра.
        Map<String, Evidence> byId = new LinkedHashMap<>();
        cs.evidences().forEach(e -> byId.put(e.id(), e));
        List<Map<String, String>> clues = ep.clueBreakdown().stream()
            .map((EpilogueClue c) -> {
                Evidence ev = byId.get(c.evidenceId());
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("evidence_id",  c.evidenceId());
                entry.put("title",        ev != null ? ev.title() : c.evidenceId());
                entry.put("explanation",  c.explanation());
                return entry;
            })
            .toList();

        var resp = new LinkedHashMap<String, Object>();
        resp.put("case_id",          cs.id());
        resp.put("answer",           cs.answer());
        resp.put("headline",         ep.headline());
        resp.put("story",            ep.story());
        resp.put("timeline",         ep.timeline());
        resp.put("clue_breakdown",   clues);
        resp.put("official_misses",  ep.officialMisses());
        resp.put("sentence",         ep.sentence());
        resp.put("press_note",       ep.pressNote());
        return resp;
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

    private static List<Evidence> filterEvidences(Case cs, List<String> unlocked) {
        return cs.evidences().stream()
            .filter(e -> unlocked.contains(e.sourceDoc()))
            .toList();
    }

    private void appendLog(ProgressEntity prog, String action) {
        List<Map<String, Object>> log = new ArrayList<>(parseLog(prog.getActionsLog()));
        log.add(Map.of("action", action, "time", LocalDateTime.now().toString()));
        prog.setActionsLog(writeJson(log));
    }

    private boolean hasLogEntry(ProgressEntity prog, String action) {
        return parseLog(prog.getActionsLog()).stream()
            .anyMatch(entry -> action.equals(entry.get("action")));
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
