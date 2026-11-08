package de.noisebleed.lesen.words;

import java.util.ArrayList;
import java.util.List;

/** Verlauf eines Wortes über alle Runden: Stapel, Anzahl und Lesezeiten. */
public final class WordStats {

    /** Für den Durchschnitt zählen nur die letzten paar Male, damit Fortschritt sichtbar wird. */
    public static final int RECENT = 5;

    public final String word;
    private final List<Long> millis = new ArrayList<>();
    private int good;
    private Boolean lastGood;

    public WordStats(String word) {
        this.word = word;
    }

    /** In zeitlicher Reihenfolge aufrufen; die letzte Bewertung bestimmt den Stapel. */
    public void add(long millis, boolean good) {
        this.millis.add(millis);
        if (good) {
            this.good++;
        }
        lastGood = good;
    }

    public Pile pile() {
        if (lastGood == null) {
            return Pile.NEW;
        }
        return lastGood ? Pile.GOOD : Pile.PRACTICE;
    }

    public int count() {
        return millis.size();
    }

    public int goodCount() {
        return good;
    }

    /** Durchschnitt der letzten {@link #RECENT} Lesezeiten, 0 wenn nie gelesen. */
    public long recentAverageMillis() {
        int n = Math.min(RECENT, millis.size());
        if (n == 0) {
            return 0;
        }
        long sum = 0;
        for (int i = millis.size() - n; i < millis.size(); i++) {
            sum += millis.get(i);
        }
        return sum / n;
    }
}
