package de.noisebleed.lesen.words;

/** Auf welchem Stapel ein Wort liegt; entscheidet die letzte Bewertung. */
public enum Pile {
    /** Grün: konnte ich schon lesen. */
    GOOD,
    /** Gelb: hakte noch etwas. */
    PRACTICE,
    /** Noch nie dran gewesen. */
    NEW
}
