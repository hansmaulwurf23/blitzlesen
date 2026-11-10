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
 *
 * Die eingebaute Liste, damit die App ohne Internet und beim ersten Start etwas hat. Dieselben Wörter
 * stehen in web/listen/grundwortschatz-100.json (die Web-Tests prüfen das); weitere Listen lädt
 * {@link WordLists} von der Web-App.
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

    /** Id der eingebauten Liste; Runden ohne "list" (vor Version 1.5) gehören zu ihr. */
    public static final String DEFAULT_ID = "grundwortschatz-100";

    private static final WordList BUILT_IN = new WordList(DEFAULT_ID, "100 häufige Wörter",
            "Die 100 häufigsten Wörter aus dem Grundwortschatz für Berlin", ALL);

    private Words() {
    }

    public static WordList builtIn() {
        return BUILT_IN;
    }

    public static List<String> all() {
        return ALL;
    }
}
