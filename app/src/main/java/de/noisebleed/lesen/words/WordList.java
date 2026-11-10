package de.noisebleed.lesen.words;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Eine Wortliste im Format der Web-App (web/listen/&lt;id&gt;.json). Die id ist die Kennung, mit der
 * sich Runden ihre Liste merken; sie ändert sich nie.
 */
public final class WordList {

    public final String id;
    public final String title;
    public final String description;
    public final List<String> words;

    public WordList(String id, String title, String description, List<String> words) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.words = Collections.unmodifiableList(new ArrayList<>(words));
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
                json.optString("description", "").trim(), new ArrayList<>(words));
    }

    public JSONObject toJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (String w : words) {
            array.put(w);
        }
        return new JSONObject()
                .put("id", id)
                .put("title", title)
                .put("description", description)
                .put("words", array);
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
