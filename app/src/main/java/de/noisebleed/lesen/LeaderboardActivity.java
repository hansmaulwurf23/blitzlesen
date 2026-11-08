package de.noisebleed.lesen;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.noisebleed.lesen.stats.History;
import de.noisebleed.lesen.stats.HistoryStore;
import de.noisebleed.lesen.stats.RoundRecord;

/** Alle Runden, die meisten Wörter oben; bei Gleichstand zählen die grünen. */
public class LeaderboardActivity extends AppCompatActivity {

    /** Diese Runde hervorheben und hinscrollen (gerade gespielt). */
    public static final String EXTRA_HIGHLIGHT = "highlight";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        String highlight = getIntent().getStringExtra(EXTRA_HIGHLIGHT);
        List<RoundRecord> rounds = HistoryStore.load(this).ranked();

        RecyclerView list = findViewById(R.id.rounds);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new RoundAdapter(rounds, highlight));
        findViewById(R.id.empty).setVisibility(rounds.isEmpty() ? View.VISIBLE : View.GONE);

        int words = 0, good = 0;
        for (RoundRecord r : rounds) {
            words += r.total();
            good += r.good();
        }
        TextView summary = findViewById(R.id.summary);
        summary.setText(getResources().getQuantityString(R.plurals.leaderboard_summary, rounds.size(),
                rounds.size(), words, words == 0 ? 0 : Math.round(100f * good / words)));
        summary.setVisibility(rounds.isEmpty() ? View.GONE : View.VISIBLE);

        for (int i = 0; i < rounds.size(); i++) {
            if (rounds.get(i).id.equals(highlight)) {
                int position = i;
                list.post(() -> ((LinearLayoutManager) list.getLayoutManager())
                        .scrollToPositionWithOffset(position, list.getHeight() / 3));
                break;
            }
        }
    }

    private final class RoundAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final DateFormat dateFormat =
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.GERMANY);
        private final List<RoundRecord> rounds;
        private final String highlight;

        RoundAdapter(List<RoundRecord> rounds, String highlight) {
            this.rounds = rounds;
            this.highlight = highlight;
        }

        @Override
        public int getItemCount() {
            return rounds.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_round, parent, false);
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            RoundRecord round = rounds.get(position);
            View view = holder.itemView;

            TextView rank = view.findViewById(R.id.rank);
            rank.setText(String.valueOf(position + 1));
            int rankColor = position == 0 ? R.color.gold : position == 1 ? R.color.silver
                    : position == 2 ? R.color.bronze : R.color.divider;
            rank.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(LeaderboardActivity.this, rankColor)));
            rank.setTextColor(position < 3 ? Color.WHITE
                    : ContextCompat.getColor(LeaderboardActivity.this, R.color.text_primary));

            ((TextView) view.findViewById(R.id.total)).setText(getString(R.string.round_total, round.total()));
            ((TextView) view.findViewById(R.id.date)).setText(dateFormat.format(new Date(round.timestamp)));
            ((TextView) view.findViewById(R.id.good)).setText("✓ " + round.good());
            ((TextView) view.findViewById(R.id.practice)).setText("↻ " + round.practice());

            boolean highlighted = round.id.equals(highlight);
            MaterialCardView card = view.findViewById(R.id.card);
            card.setCardBackgroundColor(ContextCompat.getColor(LeaderboardActivity.this,
                    highlighted ? R.color.highlight : R.color.surface));
            card.setStrokeWidth(highlighted ? Math.round(2 * getResources().getDisplayMetrics().density) : 0);
        }
    }
}
