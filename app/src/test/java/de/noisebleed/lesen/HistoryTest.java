package de.noisebleed.lesen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import de.noisebleed.lesen.stats.Attempt;
import de.noisebleed.lesen.stats.History;
import de.noisebleed.lesen.stats.RoundRecord;
import de.noisebleed.lesen.words.Pile;
import de.noisebleed.lesen.words.WordStats;
import de.noisebleed.lesen.words.Words;

public class HistoryTest {

    private static RoundRecord round(String id, long timestamp, Attempt... attempts) {
        return new RoundRecord(id, timestamp, 60, Arrays.asList(attempts));
    }

    @Test
    public void lastRatingDecidesThePile() {
        History h = new History();
        // absichtlich nicht chronologisch eingefügt
        h.add(round("2", 2000, new Attempt("und", 900, true), new Attempt("aber", 3000, false)));
        h.add(round("1", 1000, new Attempt("und", 2500, false), new Attempt("aber", 1200, true)));
        Map<String, WordStats> stats = h.wordStats(Words.all());
        assertEquals(Pile.GOOD, stats.get("und").pile());
        assertEquals(Pile.PRACTICE, stats.get("aber").pile());
        assertEquals(Pile.NEW, stats.get("Zeit").pile());
        assertEquals(1700, stats.get("und").recentAverageMillis());
        assertEquals(2, stats.get("und").count());
        assertEquals(1, stats.get("und").goodCount());
        assertEquals(100, stats.size());
    }

    @Test
    public void rankingByTotalThenGoodThenEarlier() {
        History h = new History();
        RoundRecord a = round("a", 1, new Attempt("am", 1, true), new Attempt("an", 1, false));
        RoundRecord b = round("b", 2, new Attempt("am", 1, true), new Attempt("an", 1, true));
        RoundRecord c = round("c", 3, new Attempt("am", 1, true), new Attempt("an", 1, true));
        RoundRecord d = round("d", 4, new Attempt("am", 1, true));
        h.add(d);
        h.add(c);
        h.add(a);
        h.add(b);
        assertEquals(Arrays.asList(b, c, a, d), h.ranked());
        assertEquals(3, h.rank(a));
        assertSame(c, h.bestExcept(b));
        History single = new History();
        single.add(a);
        assertNull(single.bestExcept(a));
    }

    @Test
    public void jsonRoundTripAndMergeSkipsKnownRounds() throws Exception {
        History h = new History();
        h.add(round("x", 5, new Attempt("für", 1234, true), new Attempt("über", 800, false)));
        History copy = History.fromJson(new JSONObject(h.toJson().toString()));
        RoundRecord r = copy.find("x");
        assertEquals(5, r.timestamp);
        assertEquals("für", r.attempts.get(0).word);
        assertEquals(1234, r.attempts.get(0).millis);
        assertEquals(false, r.attempts.get(1).good);

        copy.add(round("y", 6, new Attempt("so", 1, true)));
        assertEquals(1, h.merge(copy));
        assertEquals(0, h.merge(copy));
        assertEquals(2, h.rounds().size());
    }

    @Test(expected = org.json.JSONException.class)
    public void rejectsForeignFiles() throws Exception {
        History.fromJson(new JSONObject("{\"format\":\"andere-app\",\"games\":[]}"));
    }

    @Test
    public void wordListHasHundredUniqueWords() {
        List<String> all = Words.all();
        assertEquals(100, all.size());
        assertEquals(100, new HashSet<>(all).size());
    }
}
