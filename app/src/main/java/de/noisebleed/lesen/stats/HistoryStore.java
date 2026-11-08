package de.noisebleed.lesen.stats;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Alle Runden als JSON-Datei im privaten Speicher der App. Ein Update lässt sie unberührt; zum
 * Gerätewechsel lässt sie sich exportieren und wieder importieren.
 */
public final class HistoryStore {

    private static final String TAG = "HistoryStore";
    private static final String FILE = "rounds.json";

    private HistoryStore() {
    }

    public static synchronized History load(Context context) {
        try (InputStream in = file(context).openRead()) {
            return History.fromJson(new JSONObject(readText(in)));
        } catch (FileNotFoundException e) {
            return new History();
        } catch (IOException | JSONException e) {
            // Lieber leer weiter als die App unbenutzbar machen; die kaputte Datei bleibt liegen,
            // bis die nächste Runde sie überschreibt.
            Log.e(TAG, "Runden nicht lesbar", e);
            return new History();
        }
    }

    public static synchronized void save(Context context, History history) throws IOException {
        AtomicFile file = file(context);
        FileOutputStream out = file.startWrite();
        try {
            out.write(toBytes(history));
            file.finishWrite(out);
        } catch (IOException | RuntimeException e) {
            file.failWrite(out);
            throw e;
        }
    }

    public static synchronized void add(Context context, RoundRecord round) throws IOException {
        History history = load(context);
        history.add(round);
        save(context, history);
    }

    public static void export(Context context, OutputStream out) throws IOException {
        out.write(toBytes(load(context)));
        out.flush();
    }

    /** Runden aus einem Export übernehmen; vorhandene bleiben, Doppelte werden übersprungen. */
    public static synchronized int importFrom(Context context, InputStream in)
            throws IOException, JSONException {
        History imported = History.fromJson(new JSONObject(readText(in)));
        History history = load(context);
        int added = history.merge(imported);
        if (added > 0) {
            save(context, history);
        }
        return added;
    }

    private static byte[] toBytes(History history) throws IOException {
        try {
            return history.toJson().toString(1).getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private static String readText(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), FILE));
    }
}
