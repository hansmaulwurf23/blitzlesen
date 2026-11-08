package de.noisebleed.lesen;

import java.util.Locale;

/** Anzeigetexte, die an mehreren Stellen gebraucht werden. */
public final class Format {

    private Format() {
    }

    /** Restzeit als m:ss, aufgerundet — "0:01" bis zur letzten Sekunde, dann "0:00". */
    public static String countdown(long millis) {
        long seconds = (millis + 999) / 1000;
        return String.format(Locale.GERMANY, "%d:%02d", seconds / 60, seconds % 60);
    }

    /** Lesezeit eines Wortes, z. B. "1,4 s". */
    public static String seconds(long millis) {
        return String.format(Locale.GERMANY, "%.1f s", millis / 1000.0);
    }
}
