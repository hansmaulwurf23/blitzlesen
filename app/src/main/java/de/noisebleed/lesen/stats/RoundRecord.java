package de.noisebleed.lesen.stats;

import java.util.Collections;
import java.util.List;

/** Eine fertig gespielte Runde für Bestenliste und Stapel. */
public final class RoundRecord {

    public final String id;
    public final long timestamp;
    public final int seconds;
    public final List<Attempt> attempts;

    public RoundRecord(String id, long timestamp, int seconds, List<Attempt> attempts) {
        this.id = id;
        this.timestamp = timestamp;
        this.seconds = seconds;
        this.attempts = Collections.unmodifiableList(attempts);
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
