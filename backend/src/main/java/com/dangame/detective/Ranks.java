package com.dangame.detective;

import java.util.List;

public final class Ranks {

    public record Rank(String name, int minCases) {}

    public static final List<Rank> ALL = List.of(
        new Rank("Стажёр",            0),
        new Rank("Младший агент",     1),
        new Rank("Агент",             3),
        new Rank("Старший агент",     5),
        new Rank("Эксперт",           8),
        new Rank("Легенда агентства", 15)
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
