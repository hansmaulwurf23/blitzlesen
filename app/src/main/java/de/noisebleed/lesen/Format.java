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

    /** Rundendauer für Sätze wie "Wörter in …": "einer Minute", "zwei Minuten", "90 Sekunden". */
    public static String duration(int seconds) {
        if (seconds == 60) {
            return "einer Minute";
        }
        if (seconds % 60 == 0) {
            int minutes = seconds / 60;
            String[] names = {"", "", "zwei", "drei", "vier", "fünf"};
            return (minutes < names.length ? names[minutes] : String.valueOf(minutes)) + " Minuten";
        }
        return seconds + " Sekunden";
    }

    /** Kurz für die Listenauswahl: "2 Min." oder "90 s". */
    public static String durationShort(int seconds) {
        return seconds % 60 == 0 ? seconds / 60 + " Min." : seconds + " s";
    }

    /**
     * Was word von other unterscheidet, als {Anfang, Ende} in word: alles zwischen dem gemeinsamen
     * Anfang und dem gemeinsamen Ende. Haus → Maus: {0, 1} ("M"), Kuh → Schuh: {0, 3} ("Sch").
     */
    public static int[] difference(String word, String other) {
        int max = Math.min(word.length(), other.length());
        int start = 0;
        while (start < max && word.charAt(start) == other.charAt(start)) {
            start++;
        }
        int end = 0;
        while (end < max - start
                && word.charAt(word.length() - 1 - end) == other.charAt(other.length() - 1 - end)) {
            end++;
        }
        return new int[]{start, word.length() - end};
    }

    /** Lesezeit eines Wortes, z. B. "1,4 s". */
    public static String seconds(long millis) {
        return String.format(Locale.GERMANY, "%.1f s", millis / 1000.0);
    }
}
