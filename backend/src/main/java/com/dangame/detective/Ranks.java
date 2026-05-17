package com.dangame.detective;

import java.util.List;

public final class Ranks {

    public record Rank(String name, int minCases) {}

    /**
     * Ранги Агентства. Первые пять дел дают повышение каждое.
     * 6-е дело — без повышения (закрепление позиции).
     * 7-е дело — повышение до Эксперта.
     * 8-9 — закрепление.
     * 10-е дело — финальный ранг Легенды.
     */
    public static final List<Rank> ALL = List.of(
        new Rank("Стажёр",              0),  // вход
        new Rank("Младший агент",       1),  // после 1-го дела
        new Rank("Агент",               2),  // после 2-го
        new Rank("Старший агент",       3),  // после 3-го
        new Rank("Оперативник",         4),  // после 4-го
        new Rank("Следователь Агентства", 5), // после 5-го
        new Rank("Эксперт по делам",    7),  // после 7-го
        new Rank("Легенда Агентства",  10)   // после 10-го
    );

    public static String calculate(int casesSolved) {
        String rank = ALL.get(0).name();
        for (Rank r : ALL) {
            if (casesSolved >= r.minCases()) {
                rank = r.name();
            }
        }
        return rank;
    }

    private Ranks() {}
}
