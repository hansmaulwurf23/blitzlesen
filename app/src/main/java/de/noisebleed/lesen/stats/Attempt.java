package de.noisebleed.lesen.stats;

/** Eine gelesene Karte: welches Wort, wie lange es gedauert hat und ob es grün oder gelb war. */
public final class Attempt {

    public final String word;
    /** Vom Aufdecken der Karte bis zum Tippen auf Grün oder Gelb, ohne Pausen der App. */
    public final long millis;
    public final boolean good;

    public Attempt(String word, long millis, boolean good) {
        this.word = word;
        this.millis = millis;
        this.good = good;
    }
}
