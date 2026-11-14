package de.noisebleed.lesen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

import de.noisebleed.lesen.words.WordList;

public class WordListTest {

    @Test
    public void readsSecondsAndValidPairsOnly() throws Exception {
        WordList list = WordList.fromJson(new JSONObject("{\"id\": \"mp\", \"seconds\": 120,"
                + " \"words\": [\"Haus\", \"Maus\", \"Laus\"],"
                + " \"pairs\": [[\"Haus\", \"Maus\"], [\"Maus\", \"Maus\"], [\"Maus\", \"Hut\"], [\"Laus\"], \"x\"]}"));
        assertEquals(120, list.seconds);
        assertEquals(Arrays.asList(Arrays.asList("Haus", "Maus")), list.pairs);
    }

    @Test
    public void withoutExtrasItIsAOneMinuteWordList() throws Exception {
        WordList list = WordList.fromJson(new JSONObject("{\"id\": \"a\", \"words\": [\"und\"], \"seconds\": 0}"));
        assertEquals(60, list.seconds);
        assertTrue(list.pairs.isEmpty());
    }

    @Test
    public void survivesTheRoundTripThroughTheCache() throws Exception {
        WordList list = new WordList("mp", "Minimalpaare", "", Arrays.asList("Kuh", "Schuh"), 90,
                Arrays.asList(Arrays.asList("Kuh", "Schuh")));
        WordList back = WordList.fromJson(list.toJson());
        assertEquals(90, back.seconds);
        assertEquals(list.pairs, back.pairs);
        assertEquals(list.words, back.words);
    }
}
