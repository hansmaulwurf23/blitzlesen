package de.noisebleed.lesen;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
}
