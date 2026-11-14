package de.noisebleed.lesen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import de.noisebleed.lesen.game.Round;
import de.noisebleed.lesen.words.Picker;
import de.noisebleed.lesen.words.Pile;

public class PickerTest {

    @Test
    public void practiceAndNewWordsComeUpMoreOftenThanGoodOnes() {
        List<String> words = Arrays.asList("gut", "gelb", "neu");
        Map<String, Pile> piles = new HashMap<>();
        piles.put("gut", Pile.GOOD);
        piles.put("gelb", Pile.PRACTICE);
        Picker picker = new Picker(words, piles, new Random(42));
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 7000; i++) {
            counts.merge(picker.next(Collections.emptySet(), null), 1, Integer::sum);
        }
        // Gewichte 1 : 3 : 3
        assertTrue(counts.get("gut") < 1300);
        assertTrue(counts.get("gelb") > 2700);
        assertTrue(counts.get("neu") > 2700);
    }

    private static final List<List<String>> PAIRS = Arrays.asList(
            Arrays.asList("Haus", "Maus"), Arrays.asList("Maus", "Laus"),
            Arrays.asList("Ball", "Fall"), Arrays.asList("Kuh", "Schuh"));
    private static final List<String> PAIR_WORDS =
            Arrays.asList("Haus", "Maus", "Laus", "Ball", "Fall", "Kuh", "Schuh");

    private static boolean isPair(String a, String b) {
        return PAIRS.contains(Arrays.asList(a, b)) || PAIRS.contains(Arrays.asList(b, a));
    }

    @Test
    public void pairPartnerComesRightAfterWithReferenceToFirst() {
        for (int seed = 1; seed <= 20; seed++) {
            Round r = new Round(60_000, new Picker(PAIR_WORDS, new HashMap<>(), new Random(seed), PAIRS));
            r.start(0);
            for (int i = 0; i < 40; i++) {
                String first = r.current();
                assertNull(r.partner());
                r.answer(true, i * 200 + 50);
                assertTrue(r.next(i * 200 + 100));
                String second = r.current();
                assertEquals(first, r.partner());
                assertTrue(first + " – " + second, isPair(first, second));
                r.answer(false, i * 200 + 150);
                assertTrue(r.next(i * 200 + 200));
                assertNotEquals("nie zweimal hintereinander", second, r.current());
            }
        }
    }

    @Test
    public void noPairTwiceWhilePairsWithUnseenWordsRemain() {
        for (int seed = 1; seed <= 50; seed++) {
            Picker picker = new Picker(PAIR_WORDS, new HashMap<>(), new Random(seed), PAIRS);
            Set<String> shown = new HashSet<>();
            Set<String> seen = new HashSet<>();
            String last = null;
            while (anyUnseen(shown)) {
                String a = picker.next(shown, last);
                shown.add(a);
                String b = picker.next(shown, a);
                shown.add(b);
                last = b;
                String key = a.compareTo(b) < 0 ? a + "–" + b : b + "–" + a;
                assertFalse(key + " doppelt bei Startwert " + seed, seen.contains(key));
                seen.add(key);
            }
        }
    }

    private static boolean anyUnseen(Set<String> shown) {
        for (List<String> p : PAIRS) {
            if (!shown.contains(p.get(0)) || !shown.contains(p.get(1))) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void weakerWordDecidesHowOftenAPairComesUp() {
        Map<String, Pile> piles = new HashMap<>();
        piles.put("Haus", Pile.GOOD);
        piles.put("Maus", Pile.GOOD);
        piles.put("Ball", Pile.GOOD);
        piles.put("Fall", Pile.PRACTICE);
        List<List<String>> pairs = Arrays.asList(Arrays.asList("Haus", "Maus"), Arrays.asList("Ball", "Fall"));
        Picker picker = new Picker(Arrays.asList("Haus", "Maus", "Ball", "Fall"), piles, new Random(7), pairs);
        int ball = 0;
        for (int i = 0; i < 4000; i++) {
            String w = picker.next(Collections.emptySet(), null);
            picker.next(Collections.emptySet(), w);
            if (w.equals("Ball") || w.equals("Fall")) {
                ball++;
            }
        }
        // Gewichte 1 : 3
        assertTrue(String.valueOf(ball), ball > 2700 && ball < 3300);
    }
}
