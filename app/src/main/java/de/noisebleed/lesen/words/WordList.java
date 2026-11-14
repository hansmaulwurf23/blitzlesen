package de.noisebleed.lesen.words;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Eine Wortliste im Format der Web-App (web/listen/&lt;id&gt;.json). Die id ist die Kennung, mit der
 * sich Runden ihre Liste merken; sie ändert sich nie.
 *
 * Optional: "seconds" (Rundendauer, sonst eine Minute) und "pairs" (Minimalpaare, deren Wörter
 * direkt nacheinander kommen, siehe {@link Picker}).
 */
public final class WordList {

    public static final int DEFAULT_SECONDS = 60;

    public final String id;
    public final String title;
    public final String description;
    public final List<String> words;
    /** Dauer einer Runde in Sekunden. */
    public final int seconds;
    /** Paare aus je zwei verschiedenen Wörtern der Liste; leer, wenn Wort für Wort gezogen wird. */
    public final List<List<String>> pairs;

    public WordList(String id, String title, String description, List<String> words) {
        this(id, title, description, words, DEFAULT_SECONDS, Collections.emptyList());
    }

    public WordList(String id, String title, String description, List<String> words, int seconds,
                    List<List<String>> pairs) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.words = Collections.unmodifiableList(new ArrayList<>(words));
        this.seconds = seconds > 0 ? seconds : DEFAULT_SECONDS;
        List<List<String>> copy = new ArrayList<>();
        for (List<String> p : pairs) {
            copy.add(Collections.unmodifiableList(new ArrayList<>(p)));
        }
        this.pairs = Collections.unmodifiableList(copy);
    }

    /** Liest eine Liste; leere und doppelte Wörter fallen weg, eine Liste ohne Wörter ist ungültig. */
    public static WordList fromJson(JSONObject json) throws JSONException {
        String id = json.getString("id").trim();
        if (id.isEmpty()) {
            throw new JSONException("Liste ohne id");
        }
        JSONArray array = json.getJSONArray("words");
        Set<String> words = new LinkedHashSet<>();
        for (int i = 0; i < array.length(); i++) {
            String w = array.getString(i).trim();
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        if (words.isEmpty()) {
            throw new JSONException("Liste " + id + " ohne Wörter");
        }
        String title = json.optString("title", "").trim();
        return new WordList(id, title.isEmpty() ? id : title,
                json.optString("description", "").trim(), new ArrayList<>(words),
                json.optInt("seconds", DEFAULT_SECONDS), readPairs(json.optJSONArray("pairs"), words));
    }

    /** Nur Paare aus zwei verschiedenen Wörtern der Liste; alles andere fällt still weg. */
    private static List<List<String>> readPairs(JSONArray array, Set<String> words) {
        List<List<String>> pairs = new ArrayList<>();
        if (array == null) {
            return pairs;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONArray p = array.optJSONArray(i);
            if (p == null || p.length() != 2) {
                continue;
            }
            String a = p.optString(0, "").trim();
            String b = p.optString(1, "").trim();
            if (!a.equals(b) && words.contains(a) && words.contains(b)) {
                pairs.add(Arrays.asList(a, b));
            }
        }
        return pairs;
    }

    public JSONObject toJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (String w : words) {
            array.put(w);
        }
        JSONObject json = new JSONObject()
                .put("id", id)
                .put("title", title)
                .put("description", description)
                .put("seconds", seconds)
                .put("words", array);
        if (!pairs.isEmpty()) {
            JSONArray pairArray = new JSONArray();
            for (List<String> p : pairs) {
                pairArray.put(new JSONArray(p));
            }
            json.put("pairs", pairArray);
        }
        return json;
    }

    /**
     * Die Listen, die die App anbietet: die geladenen in ihrer Reihenfolge, und die eingebaute, falls
     * sie dort fehlt (dann vorne) — ohne Internet gibt es also immer mindestens die 100 Wörter. Eine
     * geladene Liste mit derselben id ersetzt die eingebaute, so kommen Korrekturen auch ohne Update an.
     */
    public static List<WordList> withBuiltIn(List<WordList> downloaded, WordList builtIn) {
        List<WordList> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (WordList l : downloaded) {
            if (ids.add(l.id)) {
                result.add(l);
            }
        }
        if (!ids.contains(builtIn.id)) {
            result.add(0, builtIn);
        }
        return result;
    }

    /** Liste mit dieser id, sonst die eingebaute, sonst die erste. */
    public static WordList find(List<WordList> lists, String id, String fallbackId) {
        WordList fallback = null;
        for (WordList l : lists) {
            if (l.id.equals(id)) {
                return l;
            }
            if (l.id.equals(fallbackId)) {
                fallback = l;
            }
        }
        return fallback != null ? fallback : lists.get(0);
    }
}
