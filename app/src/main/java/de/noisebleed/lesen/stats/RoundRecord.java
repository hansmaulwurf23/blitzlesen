package de.noisebleed.lesen.stats;

import java.util.Collections;
import java.util.List;

import de.noisebleed.lesen.words.Words;

/** Eine fertig gespielte Runde für Bestenliste und Stapel. */
public final class RoundRecord {

    public final String id;
    public final long timestamp;
    public final int seconds;
    /** Id der Wortliste; null bei Runden von vor den Wortlisten, die gehören zur eingebauten. */
    public final String list;
    public final List<Attempt> attempts;

    public RoundRecord(String id, long timestamp, int seconds, List<Attempt> attempts) {
        this(id, timestamp, seconds, null, attempts);
    }

    public RoundRecord(String id, long timestamp, int seconds, String list, List<Attempt> attempts) {
        this.id = id;
        this.timestamp = timestamp;
        this.seconds = seconds;
        this.list = list;
        this.attempts = Collections.unmodifiableList(attempts);
    }

    public String listId() {
        return list != null ? list : Words.DEFAULT_ID;
    }

    public int total() {
        return attempts.size();
    }

    public int good() {
        int n = 0;
        for (Attempt a : attempts) {
            if (a.good) {
                n++;
            }
        }
        return n;
    }

    public int practice() {
        return total() - good();
    }
}
