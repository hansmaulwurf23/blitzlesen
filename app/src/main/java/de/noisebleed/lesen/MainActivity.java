package de.noisebleed.lesen;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.noisebleed.lesen.stats.History;
import de.noisebleed.lesen.stats.HistoryStore;
import de.noisebleed.lesen.stats.RoundRecord;
import de.noisebleed.lesen.update.AppUpdater;
import de.noisebleed.lesen.words.Pile;
import de.noisebleed.lesen.words.WordStats;
import de.noisebleed.lesen.words.Words;

/** Startbildschirm: die beiden Stapel, der Rekord und der große Start-Knopf. */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    /** Die Web-App auf GitHub Pages, siehe web/ und .github/workflows/pages.yml. */
    private static final String WEB_URL = "https://hansmaulwurf23.github.io/blitzlesen/";

    private View root;

    /** Pro Start nur einmal von selbst auf ein Update hinweisen, nicht bei jedem onResume. */
    private boolean updateOffered;
    /** Nach der Freigabe "Apps aus dieser Quelle" die Installation erneut anstoßen. */
    private boolean installAfterPermission;

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"), this::exportTo);
    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importFrom);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        root = findViewById(R.id.start);

        findViewById(R.id.start).setOnClickListener(v ->
                startActivity(new Intent(this, RoundActivity.class)));
        findViewById(R.id.record_card).setOnClickListener(v ->
                startActivity(new Intent(this, LeaderboardActivity.class)));
        findViewById(R.id.new_words).setOnClickListener(v -> openWords(Pile.NEW));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        if (installAfterPermission) {
            installAfterPermission = !AppUpdater.install(this);
            return;
        }
        if (AppUpdater.readyVersionName(this) != null) {
            offerUpdate();
        } else {
            AppUpdater.checkIfDue(this, (version, error) -> {
                if (version != null) {
                    offerUpdate();
                }
            });
        }
    }

    private void refresh() {
        History history = HistoryStore.load(this);
        Map<String, WordStats> stats = history.wordStats(Words.all());
        int good = 0, practice = 0, fresh = 0;
        for (WordStats s : stats.values()) {
            switch (s.pile()) {
                case GOOD:
                    good++;
                    break;
                case PRACTICE:
                    practice++;
                    break;
                default:
                    fresh++;
            }
        }
        PileView.bind(findViewById(R.id.pile_good), true, good, v -> openWords(Pile.GOOD));
        PileView.bind(findViewById(R.id.pile_practice), false, practice, v -> openWords(Pile.PRACTICE));

        TextView newWords = findViewById(R.id.new_words);
        newWords.setText(fresh > 0
                ? getResources().getQuantityString(R.plurals.new_words, fresh, fresh)
                : getString(R.string.all_words_known));

        List<RoundRecord> ranked = history.ranked();
        TextView record = findViewById(R.id.record);
        TextView split = findViewById(R.id.record_split);
        if (ranked.isEmpty()) {
            record.setText(R.string.record_none);
            split.setVisibility(View.GONE);
        } else {
            RoundRecord best = ranked.get(0);
            record.setText(getString(R.string.record, best.total()));
            split.setText(getString(R.string.round_split, best.good(), best.practice()));
            split.setVisibility(View.VISIBLE);
        }
    }

    private void openWords(Pile pile) {
        startActivity(new Intent(this, WordsActivity.class).putExtra(WordsActivity.EXTRA_PILE, pile.name()));
    }

    // ---------------------------------------------------------------- Menü

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        menu.findItem(R.id.dark_theme).setChecked(Settings.isDark(this));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.leaderboard) {
            startActivity(new Intent(this, LeaderboardActivity.class));
            return true;
        }
        if (id == R.id.words) {
            openWords(Pile.PRACTICE);
            return true;
        }
        if (id == R.id.dark_theme) {
            Settings.setDark(this, !item.isChecked());
            return true;
        }
        if (id == R.id.export || id == R.id.import_) {
            try {
                if (id == R.id.export) {
                    String date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
                    exportLauncher.launch("lesen-fortschritt-" + date + ".json");
                } else {
                    // Manche Dateimanager kennen .json nicht als application/json.
                    importLauncher.launch(new String[]{"application/json", "text/plain", "application/octet-stream"});
                }
            } catch (ActivityNotFoundException e) {
                message(getString(R.string.no_file_picker));
            }
            return true;
        }
        if (id == R.id.check_update) {
            checkForUpdate();
            return true;
        }
        if (id == R.id.about) {
            showAbout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------------------------------------------------------------- Export / Import

    private void exportTo(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) {
                throw new IOException("Datei nicht beschreibbar");
            }
            HistoryStore.export(this, out);
            message(getString(R.string.export_done));
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Export fehlgeschlagen", e);
            message(getString(R.string.export_failed, e.getMessage()));
        }
    }

    private void importFrom(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Datei nicht lesbar");
            }
            int added = HistoryStore.importFrom(this, in);
            refresh();
            message(getResources().getQuantityString(R.plurals.import_done, added, added));
        } catch (IOException | JSONException | SecurityException e) {
            Log.e(TAG, "Import fehlgeschlagen", e);
            message(getString(R.string.import_failed, e.getMessage()));
        }
    }

    private void message(String text) {
        Snackbar.make(root, text, Snackbar.LENGTH_LONG).setAnchorView(root).show();
    }

    // ---------------------------------------------------------------- Update

    private void checkForUpdate() {
        if (!AppUpdater.isAllowed(this)) {
            message(getString(R.string.update_not_allowed));
            return;
        }
        message(getString(R.string.update_checking));
        AppUpdater.check(this, (version, error) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (version != null) {
                updateOffered = false;
                offerUpdate();
            } else {
                message(error != null
                        ? getString(R.string.update_failed, error)
                        : getString(R.string.update_none, BuildConfig.VERSION_NAME));
            }
        });
    }

    private void offerUpdate() {
        String version = AppUpdater.readyVersionName(this);
        if (version == null || updateOffered || isFinishing() || isDestroyed()) {
            return;
        }
        updateOffered = true;
        Dialogs.builder(this)
                .setTitle(R.string.update_available_title)
                .setMessage(getString(R.string.update_available_text, version, BuildConfig.VERSION_NAME))
                .setPositiveButton(R.string.update_install,
                        (d, w) -> installAfterPermission = !AppUpdater.install(this))
                .setNegativeButton(R.string.update_later, null)
                .show();
    }

    private void showAbout() {
        View content = getLayoutInflater().inflate(R.layout.dialog_about, null, false);
        content.findViewById(R.id.about_web).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WEB_URL)));
            } catch (ActivityNotFoundException e) {
                message(getString(R.string.no_browser));
            }
        });
        TextView version = content.findViewById(R.id.about_version);
        version.setText(getString(R.string.about_version,
                BuildConfig.VERSION_NAME, BuildConfig.GIT_SHA));
        Dialogs.builder(this)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
