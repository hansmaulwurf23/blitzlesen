package de.noisebleed.lesen;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatTest {

    private static String part(String word, String other) {
        int[] d = Format.difference(word, other);
        return word.substring(d[0], d[1]);
    }

    @Test
    public void differenceToThePartner() {
        assertEquals("M", part("Maus", "Haus"));
        assertEquals("Sch", part("Schuh", "Kuh"));
        assertEquals("K", part("Kuh", "Schuh"));
        assertEquals("l", part("Glas", "Gras"));
        assertEquals("W", part("Wanne", "Tanne"));
        assertEquals("R", part("Rolle", "Wolle"));
        assertEquals("S", part("See", "Fee"));
        assertEquals("", part("Haus", "Haus"));
        assertEquals(2, Format.difference("aa", "aaa")[0]);
        assertEquals(2, Format.difference("aa", "aaa")[1]);
    }

    @Test
    public void durationAsInTheWebApp() {
        assertEquals("einer Minute", Format.duration(60));
        assertEquals("zwei Minuten", Format.duration(120));
        assertEquals("10 Minuten", Format.duration(600));
        assertEquals("90 Sekunden", Format.duration(90));
    }
}
