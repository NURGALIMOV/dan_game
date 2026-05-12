package com.dangame.detective.gamedata;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Реестр достижений.
 * Ключ ({@link Achievement#key}) хранится в БД (таблица achievements).
 * Метаданные (title/description/icon) — здесь, чтобы не плодить миграции при изменениях текста.
 */
public final class Achievements {

    public record Achievement(String key, String title, String description, String icon) {}

    public static final Achievement INTUITION = new Achievement(
        "intuition",
        "Интуиция",
        "Раскрыть дело с первой попытки.",
        "🎯"
    );

    public static final Achievement CASE_SOLVED = new Achievement(
        "case_solved",
        "Дело закрыто",
        "Раскрыть своё первое дело.",
        "🗂"
    );

    public static final Achievement MARATHONER = new Achievement(
        "marathoner",
        "Дотошный",
        "Открыть все документы дела.",
        "📚"
    );

    public static final Achievement PAID_PATH = new Achievement(
        "paid_path",
        "До конца",
        "Открыть полную версию расследования.",
        "🔓"
    );

    public static final Achievement EVIDENCE_MASTER = new Achievement(
        "evidence_master",
        "Аналитик",
        "Собрать все улики против настоящего убийцы при обвинении.",
        "🧩"
    );

    public static final Achievement CAREFUL = new Achievement(
        "careful",
        "Самоконтроль",
        "Не выдвигать обвинение, пока не изучены все материалы.",
        "🛡"
    );

    public static final Achievement FALSE_LEAD = new Achievement(
        "false_lead",
        "Ложный след",
        "Получить отказ из-за противоречивых улик.",
        "🌫"
    );

    public static final Map<String, Achievement> REGISTRY;

    static {
        var map = new LinkedHashMap<String, Achievement>();
        map.put(INTUITION.key(),       INTUITION);
        map.put(CASE_SOLVED.key(),     CASE_SOLVED);
        map.put(MARATHONER.key(),      MARATHONER);
        map.put(PAID_PATH.key(),       PAID_PATH);
        map.put(EVIDENCE_MASTER.key(), EVIDENCE_MASTER);
        map.put(CAREFUL.key(),         CAREFUL);
        map.put(FALSE_LEAD.key(),      FALSE_LEAD);
        REGISTRY = Map.copyOf(map);
    }

    private Achievements() {}
}
