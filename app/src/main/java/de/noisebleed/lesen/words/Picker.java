package de.noisebleed.lesen.words;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Zieht die nächste Karte zufällig, aber gewichtet: gelbe und neue Wörter kommen häufiger dran als
 * grüne, damit geübt wird, was noch hakt, und trotzdem Bekanntes wiederkommt. Innerhalb einer Runde
 * wiederholt sich kein Wort, solange es noch ungezeigte gibt, und nie zweimal direkt hintereinander.
 *
 * Mit Paaren (Minimalpaare) wird stattdessen ein Paar gezogen, gewichtet nach seinem schwächeren
 * Wort, und beide Wörter kommen direkt nacheinander, in zufälliger Reihenfolge. Kein Paar wiederholt
 * sich, solange es noch Paare mit ungezeigten Wörtern gibt; danach kommt kein Paar mit dem gerade
 * gelesenen Wort. Dieselben Regeln wie Picker in web/lesen.js.
 */
public final class Picker {

    static final int WEIGHT_GOOD = 1;
    static final int WEIGHT_PRACTICE = 3;
    static final int WEIGHT_NEW = 3;

    private interface Weight<T> {
        int of(T item);
    }

    private final List<String> words;
    private final Map<String, Pile> piles;
    private final Random random;
    private final List<List<String>> pairs;

    /** Erstes und zweites Wort des laufenden Paars, bis das zweite gezogen ist. */
    private String pendingFirst;
    private String pendingSecond;
    private String pairedWith;

    /** piles: Stapel je Wort zu Beginn der Runde; fehlende Wörter gelten als neu. */
    public Picker(List<String> words, Map<String, Pile> piles, Random random) {
        this(words, piles, random, Collections.emptyList());
    }

    /** pairs: je zwei verschiedene Wörter aus words; leer heißt Wort für Wort. */
    public Picker(List<String> words, Map<String, Pile> piles, Random random, List<List<String>> pairs) {
        this.words = words;
        this.piles = piles;
        this.random = random;
        this.pairs = pairs;
    }

    /**
     * shown: in dieser Runde schon gezeigte Wörter; last: das gerade gezeigte (oder null).
     */
    public String next(Collection<String> shown, String last) {
        pairedWith = null;
        if (pendingSecond != null) {
            String second = pendingSecond;
            pairedWith = pendingFirst;
            pendingFirst = null;
            pendingSecond = null;
            return second;
        }
        return pairs.isEmpty() ? nextWord(shown, last) : nextPair(shown, last);
    }

    /** Nach {@link #next}: das erste Wort des Paars, wenn die gezogene Karte sein Gegenwort ist. */
    public String pairedWith() {
        return pairedWith;
    }

    private String nextWord(Collection<String> shown, String last) {
        List<String> candidates = new ArrayList<>();
        for (String w : words) {
            if (!shown.contains(w)) {
                candidates.add(w);
            }
        }
        if (candidates.isEmpty()) {
            for (String w : words) {
                if (!w.equals(last)) {
                    candidates.add(w);
                }
            }
        }
        if (candidates.isEmpty()) {
            return words.get(0);
        }
        return pick(candidates, this::weight);
    }

    private String nextPair(Collection<String> shown, String last) {
        List<List<String>> candidates = new ArrayList<>();
        for (List<String> p : pairs) {
            if (!shown.contains(p.get(0)) || !shown.contains(p.get(1))) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) {
            for (List<String> p : pairs) {
                if (!p.contains(last)) {
                    candidates.add(p);
                }
            }
        }
        if (candidates.isEmpty()) {
            candidates.addAll(pairs);
        }
        List<String> pair = pick(candidates, p -> Math.max(weight(p.get(0)), weight(p.get(1))));
        boolean swap = random.nextBoolean();
        String first = pair.get(swap ? 1 : 0);
        String second = pair.get(swap ? 0 : 1);
        if (first.equals(last)) {
            String t = first;
            first = second;
            second = t;
        }
        pendingFirst = first;
        pendingSecond = second;
        return first;
    }

    /** Eines aus candidates, mit Wahrscheinlichkeit proportional zum Gewicht. */
    private <T> T pick(List<T> candidates, Weight<T> weight) {
        int total = 0;
        for (T c : candidates) {
            total += weight.of(c);
        }
        int r = random.nextInt(total);
        for (T c : candidates) {
            r -= weight.of(c);
            if (r < 0) {
                return c;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    int weight(String word) {
        Pile pile = piles.get(word);
        if (pile == null || pile == Pile.NEW) {
            return WEIGHT_NEW;
        }
        return pile == Pile.GOOD ? WEIGHT_GOOD : WEIGHT_PRACTICE;
    }
}
