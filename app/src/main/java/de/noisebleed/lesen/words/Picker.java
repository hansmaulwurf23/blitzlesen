package de.noisebleed.lesen.words;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Zieht die nächste Karte zufällig, aber gewichtet: gelbe und neue Wörter kommen häufiger dran als
 * grüne, damit geübt wird, was noch hakt, und trotzdem Bekanntes wiederkommt. Innerhalb einer Runde
 * wiederholt sich kein Wort, solange es noch ungezeigte gibt, und nie zweimal direkt hintereinander.
 */
public final class Picker {

    static final int WEIGHT_GOOD = 1;
    static final int WEIGHT_PRACTICE = 3;
    static final int WEIGHT_NEW = 3;

    private final List<String> words;
    private final Map<String, Pile> piles;
    private final Random random;

    /** piles: Stapel je Wort zu Beginn der Runde; fehlende Wörter gelten als neu. */
    public Picker(List<String> words, Map<String, Pile> piles, Random random) {
        this.words = words;
        this.piles = piles;
        this.random = random;
    }

    /**
     * shown: in dieser Runde schon gezeigte Wörter; last: das gerade gezeigte (oder null).
     */
    public String next(Collection<String> shown, String last) {
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
        int total = 0;
        for (String w : candidates) {
            total += weight(w);
        }
        int r = random.nextInt(total);
        for (String w : candidates) {
            r -= weight(w);
            if (r < 0) {
                return w;
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
