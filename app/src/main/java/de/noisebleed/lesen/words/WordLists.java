package de.noisebleed.lesen.words;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.noisebleed.lesen.BuildConfig;

/**
 * Die Wortlisten der App. Neue Listen kommen von der Web-App (GitHub Pages, web/listen/), damit sie
 * ohne App-Update erscheinen: erst listen/index.json mit den ids, dann je Liste listen/&lt;id&gt;.json.
 * Was einmal geladen ist, liegt in files/listen.json und gilt auch offline. Die 100 Wörter sind
 * zusätzlich fest eingebaut ({@link Words}).
 */
public final class WordLists {

    public interface Listener {
        /** Auf dem Main-Thread, nur wenn sich an den Listen etwas geändert hat. */
        void onChanged();
    }

    private static final String TAG = "WordLists";
    private static final String FILE = "listen.json";
    private static final String PREFS = "word_lists";
    private static final String KEY_SELECTED = "selected";
    private static final String KEY_LAST_CHECK = "last_check";
    /** Zwei kleine Dateien — öfter als das Update der App, aber nicht bei jedem Zurückkehren. */
    private static final long CHECK_INTERVAL_MS = 30 * 60 * 1000L;
    private static final int TIMEOUT_MS = 15_000;
    /** Schutz vor einer kaputten oder fremden Antwort; die 100 Wörter sind gut 1 KB. */
    private static final int MAX_BYTES = 512 * 1024;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static List<WordList> cached;

    private WordLists() {
    }

    /** Alle angebotenen Listen, mindestens die eingebaute. */
    public static synchronized List<WordList> all(Context context) {
        if (cached == null) {
            cached = WordList.withBuiltIn(load(context), Words.builtIn());
        }
        return cached;
    }

    /** Die gewählte Liste; ist sie verschwunden, die eingebaute. */
    public static WordList current(Context context) {
        return find(context, prefs(context).getString(KEY_SELECTED, Words.DEFAULT_ID));
    }

    public static WordList find(Context context, String id) {
        return WordList.find(all(context), id, Words.DEFAULT_ID);
    }

    public static void select(Context context, String id) {
        prefs(context).edit().putString(KEY_SELECTED, id).apply();
    }

    /** Gibt es etwas zum Umschalten? Sonst bleibt die Oberfläche wie vor den Wortlisten. */
    public static boolean hasChoice(Context context) {
        return all(context).size() > 1;
    }

    // ---------------------------------------------------------------- Laden

    /** Beim Start: höchstens alle halbe Stunde bei der Web-App nachsehen. */
    public static void checkIfDue(Context context, Listener listener) {
        long last = prefs(context).getLong(KEY_LAST_CHECK, 0);
        if (System.currentTimeMillis() - last >= CHECK_INTERVAL_MS) {
            refresh(context, listener);
        }
    }

    public static void refresh(Context context, Listener listener) {
        Context app = context.getApplicationContext();
        // Auch bei Fehlern (offline) erst im nächsten Intervall wieder fragen.
        prefs(app).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
        executor.execute(() -> {
            try {
                List<WordList> lists = download();
                if (save(app, lists)) {
                    synchronized (WordLists.class) {
                        cached = null;
                    }
                    main.post(listener::onChanged);
                }
            } catch (IOException | JSONException e) {
                // Kein Netz oder die Seite ist gerade kaputt: die bisherigen Listen bleiben.
                Log.w(TAG, "Wortlisten nicht geladen: " + e.getMessage());
            }
        });
    }

    /**
     * Alle Listen laut Index. Eine einzelne unlesbare Liste wird übersprungen, ein Netzfehler bricht
     * ab — sonst verschwänden bei schlechtem Empfang Listen, die nur gerade nicht ankamen.
     */
    private static List<WordList> download() throws IOException, JSONException {
        String base = BuildConfig.WEB_URL + "listen/";
        JSONArray ids = new JSONObject(readText(base + "index.json")).getJSONArray("lists");
        List<WordList> lists = new ArrayList<>();
        for (int i = 0; i < ids.length(); i++) {
            String id = ids.getString(i);
            if (!id.matches("[a-z0-9-]+")) {
                Log.w(TAG, "Ungültige Listen-id: " + id);
                continue;
            }
            try {
                WordList list = WordList.fromJson(new JSONObject(readText(base + id + ".json")));
                if (list.id.equals(id)) {
                    lists.add(list);
                } else {
                    Log.w(TAG, "id passt nicht zur Datei: " + id + " / " + list.id);
                }
            } catch (JSONException e) {
                Log.w(TAG, "Liste " + id + " unlesbar: " + e.getMessage());
            }
        }
        if (lists.isEmpty()) {
            throw new JSONException("Keine gültige Liste im Index");
        }
        return lists;
    }

    private static String readText(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "Lesen/" + BuildConfig.VERSION_NAME);
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " für " + url);
            }
            try (InputStream in = connection.getInputStream()) {
                return readAll(in);
            }
        } finally {
            connection.disconnect();
        }
    }

    // ---------------------------------------------------------------- Speicher

    private static List<WordList> load(Context context) {
        try (InputStream in = file(context).openRead()) {
            JSONArray array = new JSONObject(readAll(in)).getJSONArray("lists");
            List<WordList> lists = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                lists.add(WordList.fromJson(array.getJSONObject(i)));
            }
            return lists;
        } catch (FileNotFoundException e) {
            return Collections.emptyList();
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Gespeicherte Wortlisten unlesbar", e);
            return Collections.emptyList();
        }
    }

    /** Speichert, wenn sich etwas geändert hat; true dann. */
    private static synchronized boolean save(Context context, List<WordList> lists)
            throws IOException, JSONException {
        JSONArray array = new JSONArray();
        for (WordList l : lists) {
            array.put(l.toJson());
        }
        byte[] bytes = new JSONObject().put("lists", array).toString(1).getBytes(StandardCharsets.UTF_8);
        AtomicFile file = file(context);
        try (InputStream in = file.openRead()) {
            if (readAll(in).equals(new String(bytes, StandardCharsets.UTF_8))) {
                return false;
            }
        } catch (FileNotFoundException ignored) {
            // erstes Mal
        }
        FileOutputStream out = file.startWrite();
        try {
            out.write(bytes);
            file.finishWrite(out);
        } catch (IOException | RuntimeException e) {
            file.failWrite(out);
            throw e;
        }
        return true;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
            if (out.size() > MAX_BYTES) {
                throw new IOException("Antwort zu groß");
            }
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), FILE));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
