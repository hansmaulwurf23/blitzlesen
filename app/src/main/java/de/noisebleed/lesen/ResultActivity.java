package de.noisebleed.lesen;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import de.noisebleed.lesen.stats.Attempt;
import de.noisebleed.lesen.stats.History;
import de.noisebleed.lesen.stats.HistoryStore;
import de.noisebleed.lesen.stats.RoundRecord;
import de.noisebleed.lesen.words.WordLists;

/** Nach der Runde: wie viele Wörter, wie viele grün und gelb, Rekord und alle Karten mit Zeit. */
public class ResultActivity extends AppCompatActivity {

    public static final String EXTRA_ROUND = "round";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        String id = getIntent().getStringExtra(EXTRA_ROUND);
        History history = HistoryStore.load(this);
        RoundRecord round = id != null ? history.find(id) : null;
        if (round == null) {
            finish();
            return;
        }

        int total = round.total();
        double share = total == 0 ? 0 : (double) round.good() / total;
        ((TextView) findViewById(R.id.title)).setText(share >= 0.8 ? R.string.result_title_great
                : share >= 0.5 ? R.string.result_title_good : R.string.result_title_practice);

        if (WordLists.hasChoice(this)) {
            ((TextView) findViewById(R.id.words_label)).setText(getString(R.string.result_words_label_list,
                    WordLists.find(this, round.listId()).title));
        }

        TextView totalView = findViewById(R.id.total);
        totalView.setText(String.valueOf(total));
        pop(totalView, 0);

        PileView.bind(findViewById(R.id.pile_good), true, round.good(), null);
        PileView.bind(findViewById(R.id.pile_practice), false, round.practice(), null);

        RoundRecord best = history.bestExcept(round);
        if (best != null && History.RANKING.compare(round, best) < 0) {
            View record = findViewById(R.id.new_record);
            record.setVisibility(View.VISIBLE);
            pop(record, 300);
        }
        ((TextView) findViewById(R.id.rank)).setText(getString(R.string.result_rank, history.rank(round)));

        Attempt fastest = null;
        for (Attempt a : round.attempts) {
            if (a.good && (fastest == null || a.millis < fastest.millis)) {
                fastest = a;
            }
        }
        TextView fastestView = findViewById(R.id.fastest);
        if (fastest != null) {
            fastestView.setText(getString(R.string.result_fastest, fastest.word, Format.seconds(fastest.millis)));
        } else {
            fastestView.setVisibility(View.GONE);
        }

        ChipGroup chips = findViewById(R.id.word_chips);
        int good = ContextCompat.getColor(this, R.color.good);
        int practice = ContextCompat.getColor(this, R.color.practice);
        int onPractice = ContextCompat.getColor(this, R.color.on_practice);
        for (Attempt a : round.attempts) {
            Chip chip = new Chip(this);
            chip.setText(a.word + "  " + Format.seconds(a.millis));
            chip.setTextSize(16);
            chip.setChipBackgroundColor(ColorStateList.valueOf(a.good ? good : practice));
            chip.setTextColor(a.good ? 0xFFFFFFFF : onPractice);
            chip.setChipStrokeWidth(0);
            chip.setClickable(false);
            chips.addView(chip);
        }

        findViewById(R.id.to_leaderboard).setOnClickListener(v -> {
            startActivity(new Intent(this, LeaderboardActivity.class)
                    .putExtra(LeaderboardActivity.EXTRA_HIGHLIGHT, round.id));
            finish();
        });
        findViewById(R.id.play_again).setOnClickListener(v -> {
            startActivity(new Intent(this, RoundActivity.class));
            finish();
        });
    }

    private static void pop(View view, long delay) {
        view.setScaleX(0.3f);
        view.setScaleY(0.3f);
        view.animate().scaleX(1f).scaleY(1f).setStartDelay(delay).setDuration(450)
                .setInterpolator(new OvershootInterpolator(2.5f)).start();
    }
}
