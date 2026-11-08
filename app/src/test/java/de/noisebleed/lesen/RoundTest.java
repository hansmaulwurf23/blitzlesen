package de.noisebleed.lesen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import de.noisebleed.lesen.game.Round;
import de.noisebleed.lesen.stats.Attempt;
import de.noisebleed.lesen.words.Picker;
import de.noisebleed.lesen.words.Words;

public class RoundTest {

    private static Round round(List<String> words) {
        return new Round(60_000, new Picker(words, new HashMap<>(), new Random(1)));
    }

    @Test
    public void measuresTimePerWordWithoutPauses() {
        Round r = round(Words.all());
        r.start(1_000);
        String first = r.current();
        assertNotNull(first);
        r.pause(2_000);
        r.resume(10_000);   // 8 s Pause zählen nicht
        Attempt a = r.answer(true, 10_500);
        assertEquals(first, a.word);
        assertEquals(1_500, a.millis);
        assertNull(r.current());

        assertTrue(r.next(11_000));  // Zeit zwischen den Karten zählt zur Runde, nicht zum Wort
        Attempt b = r.answer(false, 13_000);
        assertEquals(2_000, b.millis);
        assertEquals(1, r.goodCount());
        assertEquals(1, r.practiceCount());
        assertEquals(60_000 - 4_000, r.remaining(13_000));
    }

    @Test
    public void openCardMayBeFinishedAfterTimeIsUp() {
        Round r = round(Words.all());
        r.start(0);
        r.answer(true, 59_000);
        assertTrue(r.next(59_500));
        assertTrue(r.isTimeUp(61_000));
        assertNotNull(r.current());
        r.answer(true, 62_000);     // zählt noch
        assertFalse(r.next(62_200));
        assertTrue(r.isFinished());
        assertEquals(2, r.attempts().size());
    }

    @Test
    public void noRepeatsUntilAllShownAndNeverTwiceInARow() {
        List<String> words = Arrays.asList("a", "b", "c");
        Round r = round(words);
        r.start(0);
        Set<String> seen = new HashSet<>();
        String last = null;
        for (int i = 0; i < 30; i++) {
            String w = r.current();
            if (i < 3) {
                assertTrue("Wiederholung vor Ende des Durchgangs: " + w, seen.add(w));
            }
            assertNotEquals(last, w);
            last = w;
            r.answer(true, i * 100L + 50);
            assertTrue(r.next(i * 100L + 100));
        }
    }
}
