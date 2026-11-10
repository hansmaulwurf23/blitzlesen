package de.noisebleed.lesen.stats;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.noisebleed.lesen.words.Pile;
import de.noisebleed.lesen.words.WordStats;

/**
 * Alle gespielten Runden. Bestenliste und Stapel werden daraus abgeleitet, gespeichert wird nur die
 * Liste der Runden — so ergibt ein Import auf einem anderen Gerät automatisch auch dieselben Stapel.
 */
public final class History {

    public static final String FORMAT = "lesen-runden";
    public static final int VERSION = 1;

    /** Mehr Wörter zuerst, bei Gleichstand mehr grüne, dann die frühere Runde. */
    public static final Comparator<RoundRecord> RANKING = (a, b) -> {
        if (a.total() != b.total()) {
            return Integer.compare(b.total(), a.total());
        }
        if (a.good() != b.good()) {
            return Integer.compare(b.good(), a.good());
        }
        return Long.compare(a.timestamp, b.timestamp);
    };

    private final List<RoundRecord> rounds = new ArrayList<>();

    public List<RoundRecord> rounds() {
        return Collections.unmodifiableList(rounds);
    }

    public void add(RoundRecord round) {
        rounds.add(round);
    }

    public RoundRecord find(String id) {
        for (RoundRecord r : rounds) {
            if (r.id.equals(id)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Bestenliste einer Wortliste. Jede Liste hat ihre eigene, weil Listen unterschiedlich schwer
     * sind; Stapel dagegen zählen über alle Listen (siehe {@link #wordStats}).
     */
    public List<RoundRecord> ranked(String listId) {
        List<RoundRecord> sorted = new ArrayList<>();
        for (RoundRecord r : rounds) {
            if (r.listId().equals(listId)) {
                sorted.add(r);
            }
        }
        sorted.sort(RANKING);
        return sorted;
    }

    /** Platz 1, 2, … in der Bestenliste ihrer Wortliste. */
    public int rank(RoundRecord round) {
        return ranked(round.listId()).indexOf(round) + 1;
    }

    /** Beste andere Runde derselben Liste — null, wenn es die erste ist. Für "Neuer Rekord!". */
    public RoundRecord bestExcept(RoundRecord round) {
        RoundRecord best = null;
        for (RoundRecord r : rounds) {
            if (r != round && r.listId().equals(round.listId())
                    && (best == null || RANKING.compare(r, best) < 0)) {
                best = r;
            }
        }
        return best;
    }

    /**
     * Verlauf je Wort, für jedes Wort aus words (auch nie gelesene), in dieser Reihenfolge. Gezählt
     * wird über alle Wortlisten: Wer "und" in einer Liste kann, kann es auch in der nächsten.
     */
    public Map<String, WordStats> wordStats(List<String> words) {
        Map<String, WordStats> stats = new LinkedHashMap<>();
        for (String w : words) {
            stats.put(w, new WordStats(w));
        }
        List<RoundRecord> chronological = new ArrayList<>(rounds);
        chronological.sort(Comparator.comparingLong(r -> r.timestamp));
        for (RoundRecord r : chronological) {
            for (Attempt a : r.attempts) {
                WordStats s = stats.get(a.word);
                // Wörter, die es in dieser Version nicht (mehr) gibt, bleiben gespeichert, zählen
                // aber für keinen Stapel.
                if (s != null) {
                    s.add(a.millis, a.good);
                }
            }
        }
        return stats;
    }

    public static Map<String, Pile> piles(Map<String, WordStats> stats) {
        Map<String, Pile> piles = new LinkedHashMap<>();
        for (WordStats s : stats.values()) {
            piles.put(s.word, s.pile());
        }
        return piles;
    }

    /** Ergänzt um Runden mit unbekannter id; gibt die Anzahl neuer Runden zurück. */
    public int merge(History other) {
        Set<String> known = new HashSet<>();
        for (RoundRecord r : rounds) {
            known.add(r.id);
        }
        int added = 0;
        for (RoundRecord r : other.rounds) {
            if (known.add(r.id)) {
                rounds.add(r);
                added++;
            }
        }
        return added;
    }

    // ---------------------------------------------------------------- JSON

    public JSONObject toJson() throws JSONException {
        JSONArray list = new JSONArray();
        for (RoundRecord r : rounds) {
            JSONArray attempts = new JSONArray();
            for (Attempt a : r.attempts) {
                attempts.put(new JSONObject()
                        .put("word", a.word)
                        .put("millis", a.millis)
                        .put("good", a.good));
            }
            JSONObject round = new JSONObject()
                    .put("id", r.id)
                    .put("timestamp", r.timestamp)
                    .put("seconds", r.seconds);
            if (r.list != null) {
                round.put("list", r.list);
            }
            list.put(round.put("attempts", attempts));
        }
        return new JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("rounds", list);
    }

    public static History fromJson(JSONObject json) throws JSONException {
        if (!FORMAT.equals(json.optString("format"))) {
            throw new JSONException("Keine Lesen-Datei");
        }
        if (json.optInt("version") > VERSION) {
            throw new JSONException("Datei von einer neueren App-Version");
        }
        History history = new History();
        JSONArray list = json.getJSONArray("rounds");
        for (int i = 0; i < list.length(); i++) {
            JSONObject r = list.getJSONObject(i);
            JSONArray attemptsJson = r.getJSONArray("attempts");
            List<Attempt> attempts = new ArrayList<>();
            for (int j = 0; j < attemptsJson.length(); j++) {
                JSONObject a = attemptsJson.getJSONObject(j);
                attempts.add(new Attempt(a.getString("word"), a.getLong("millis"), a.getBoolean("good")));
            }
            String listId = r.optString("list", "").trim();
            history.add(new RoundRecord(r.getString("id"), r.getLong("timestamp"),
                    r.optInt("seconds", 60), listId.isEmpty() ? null : listId, attempts));
        }
        return history;
    }
}
