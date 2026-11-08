package de.noisebleed.lesen.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.noisebleed.lesen.stats.Attempt;
import de.noisebleed.lesen.words.Picker;

/**
 * Eine Runde Blitzlesen: zufällige Karten, bis die Zeit um ist. Eine Karte, die beim Ablauf noch
 * offen ist, darf noch bewertet werden — wer mitten im Wort ist, soll es zu Ende lesen dürfen.
 *
 * Die Zeit läuft nur, solange die Runde nicht pausiert ist (App im Hintergrund). Alle Zeitpunkte
 * kommen von außen (z. B. SystemClock.elapsedRealtime()), damit sich das ohne Android testen lässt.
 */
public final class Round {

    private final long durationMillis;
    private final Picker picker;

    private final List<Attempt> attempts = new ArrayList<>();
    private final Set<String> shown = new LinkedHashSet<>();

    /** Bisher gelaufene Zeit bis zum letzten Pausieren. */
    private long banked;
    /** Seit wann die Uhr läuft, oder -1 wenn sie steht. */
    private long runningSince = -1;

    private String current;
    private String last;
    /** Laufzeit der Runde, als die aktuelle Karte aufgedeckt wurde. */
    private long shownAt;
    private boolean started;
    private boolean finished;

    public Round(long durationMillis, Picker picker) {
        this.durationMillis = durationMillis;
        this.picker = picker;
    }

    /** Uhr starten und die erste Karte aufdecken. */
    public void start(long now) {
        if (started) {
            return;
        }
        started = true;
        runningSince = now;
        next(now);
    }

    public void pause(long now) {
        if (runningSince >= 0) {
            banked += now - runningSince;
            runningSince = -1;
        }
    }

    public void resume(long now) {
        if (started && !finished && runningSince < 0) {
            runningSince = now;
        }
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isFinished() {
        return finished;
    }

    public long elapsed(long now) {
        return banked + (runningSince >= 0 ? now - runningSince : 0);
    }

    public long remaining(long now) {
        return Math.max(0, durationMillis - elapsed(now));
    }

    public boolean isTimeUp(long now) {
        return elapsed(now) >= durationMillis;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    /** Das gerade aufgedeckte Wort, oder null zwischen zwei Karten. */
    public String current() {
        return current;
    }

    /** Aktuelle Karte grün (good) oder gelb bewerten; danach ist bis {@link #next} keine offen. */
    public Attempt answer(boolean good, long now) {
        if (current == null || finished) {
            throw new IllegalStateException("Keine offene Karte");
        }
        Attempt attempt = new Attempt(current, elapsed(now) - shownAt, good);
        attempts.add(attempt);
        last = current;
        current = null;
        return attempt;
    }

    /** Nächste Karte aufdecken. False, wenn die Zeit um ist — dann ist die Runde beendet. */
    public boolean next(long now) {
        if (finished) {
            return false;
        }
        if (isTimeUp(now)) {
            current = null;
            finished = true;
            pause(now);
            return false;
        }
        current = picker.next(shown, last);
        shown.add(current);
        shownAt = elapsed(now);
        return true;
    }

    public List<Attempt> attempts() {
        return Collections.unmodifiableList(attempts);
    }

    public int goodCount() {
        int n = 0;
        for (Attempt a : attempts) {
            if (a.good) {
                n++;
            }
        }
        return n;
    }

    public int practiceCount() {
        return attempts.size() - goodCount();
    }
}
