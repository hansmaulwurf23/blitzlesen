package de.noisebleed.lesen.words;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Die 100 häufigsten Wörter aus dem Berliner Grundwortschatz (Planungshilfe für den
 * Rechtschreibunterricht, Fassung 2025, S. 22–23), alphabetisch. "Zeit" bleibt als Nomen groß.
 *
 * Das Wort selbst ist die Kennung in gespeicherten Runden und im Export — ein Wort umzubenennen
 * heißt also, seinen Verlauf zu verlieren.
 */
public final class Words {

    private static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            "aber", "alle", "als", "am", "an", "auch", "auf", "aus", "bei", "bis",
            "da", "dann", "das", "dass", "dem", "den", "denn", "der", "des", "die",
            "diese", "dieser", "doch", "du", "durch", "ein", "eine", "einem", "einen", "einer",
            "eines", "einzelnen", "er", "es", "für", "ganz", "gegen", "habe", "haben", "hat",
            "hatte", "hier", "ich", "ihm", "ihn", "ihnen", "ihr", "ihre", "im", "in",
            "ist", "kann", "können", "man", "mehr", "meine", "mich", "mir", "mit", "muss",
            "nach", "nicht", "noch", "nun", "nur", "oder", "ohne", "schon", "sehr", "sein",
            "seine", "seiner", "selbst", "sich", "sie", "sind", "so", "über", "um", "und",
            "uns", "unter", "viel", "vom", "von", "vor", "war", "was", "welche", "wenn",
            "werden", "wie", "wieder", "wir", "wird", "wo", "Zeit", "zu", "zum", "zur"));

    private Words() {
    }

    public static List<String> all() {
        return ALL;
    }
}
