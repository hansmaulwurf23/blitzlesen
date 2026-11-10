package de.noisebleed.lesen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import de.noisebleed.lesen.stats.Attempt;
import de.noisebleed.lesen.stats.History;
import de.noisebleed.lesen.stats.RoundRecord;
import de.noisebleed.lesen.words.Pile;
import de.noisebleed.lesen.words.WordList;
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
        assertEquals(Arrays.asList(b, c, a, d), h.ranked(Words.DEFAULT_ID));
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

    @Test
    public void leaderboardPerListPilesAcrossLists() {
        History h = new History();
        // ohne Liste: von vor Version 1.5, gehört zur eingebauten
        RoundRecord old = round("alt", 1, new Attempt("und", 900, true), new Attempt("am", 900, true),
                new Attempt("an", 900, true));
        RoundRecord other = new RoundRecord("neu", 2, 60, "tiere",
                Arrays.asList(new Attempt("und", 1200, false)));
        h.add(old);
        h.add(other);
        assertEquals(Collections.singletonList(old), h.ranked(Words.DEFAULT_ID));
        assertEquals(Collections.singletonList(other), h.ranked("tiere"));
        assertEquals(1, h.rank(other));
        // Rekord nur innerhalb derselben Liste
        assertNull(h.bestExcept(other));
        // Stapel über alle Listen: zuletzt hakte "und"
        assertEquals(Pile.PRACTICE, h.wordStats(Words.all()).get("und").pile());
    }

    @Test
    public void listSurvivesJsonAndOldRoundsStayWithout() throws Exception {
        History h = new History();
        h.add(round("a", 1, new Attempt("so", 1, true)));
        h.add(new RoundRecord("b", 2, 60, "tiere", Arrays.asList(new Attempt("Hund", 1, true))));
        JSONObject json = h.toJson();
        assertTrue(!json.getJSONArray("rounds").getJSONObject(0).has("list"));
        History copy = History.fromJson(new JSONObject(json.toString()));
        assertNull(copy.find("a").list);
        assertEquals(Words.DEFAULT_ID, copy.find("a").listId());
        assertEquals("tiere", copy.find("b").list);
    }

    @Test
    public void wordListFromWebFormat() throws Exception {
        WordList l = WordList.fromJson(new JSONObject(
                "{\"id\":\"tiere\",\"title\":\"Tiere\",\"words\":[\"Hund\",\" Katze \",\"\",\"Hund\"]}"));
        assertEquals("tiere", l.id);
        assertEquals("Tiere", l.title);
        assertEquals("", l.description);
        assertEquals(Arrays.asList("Hund", "Katze"), l.words);
        assertEquals(l.words, WordList.fromJson(new JSONObject(l.toJson().toString())).words);
    }

    @Test(expected = org.json.JSONException.class)
    public void wordListWithoutWordsIsInvalid() throws Exception {
        WordList.fromJson(new JSONObject("{\"id\":\"leer\",\"words\":[\" \"]}"));
    }

    @Test
    public void builtInListIsAlwaysOffered() {
        WordList builtIn = Words.builtIn();
        WordList tiere = new WordList("tiere", "Tiere", "", Arrays.asList("Hund"));
        WordList fixed = new WordList(Words.DEFAULT_ID, "Korrigiert", "", Arrays.asList("und"));

        List<WordList> offline = WordList.withBuiltIn(Collections.emptyList(), builtIn);
        assertEquals(Collections.singletonList(builtIn), offline);
        // fehlt sie im Download, kommt sie nach vorne
        assertEquals(Arrays.asList(builtIn, tiere), WordList.withBuiltIn(Arrays.asList(tiere), builtIn));
        // eine geladene mit derselben id ersetzt sie, Reihenfolge wie im Index
        assertEquals(Arrays.asList(tiere, fixed), WordList.withBuiltIn(Arrays.asList(tiere, fixed), builtIn));

        List<WordList> lists = Arrays.asList(tiere, builtIn);
        assertSame(tiere, WordList.find(lists, "tiere", Words.DEFAULT_ID));
        assertSame(builtIn, WordList.find(lists, "gibt-es-nicht", Words.DEFAULT_ID));
        assertSame(tiere, WordList.find(Collections.singletonList(tiere), "weg", Words.DEFAULT_ID));
    }
}
