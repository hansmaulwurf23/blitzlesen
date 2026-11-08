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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.noisebleed.lesen.stats.HistoryStore;
import de.noisebleed.lesen.words.Pile;
import de.noisebleed.lesen.words.WordStats;
import de.noisebleed.lesen.words.Words;

/** Was auf welchem Stapel liegt, je Wort mit durchschnittlicher Lesezeit. */
public class WordsActivity extends AppCompatActivity {

    public static final String EXTRA_PILE = "pile";
    private static final String STATE_PILE = "pile";

    private Map<String, WordStats> stats;
    private Pile pile = Pile.PRACTICE;
    private final WordAdapter adapter = new WordAdapter();
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_words);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        String requested = savedInstanceState != null ? savedInstanceState.getString(STATE_PILE)
                : getIntent().getStringExtra(EXTRA_PILE);
        if (requested != null) {
            try {
                pile = Pile.valueOf(requested);
            } catch (IllegalArgumentException ignored) {
                // bleibt bei "hakt noch"
            }
        }

        stats = HistoryStore.load(this).wordStats(Words.all());
        empty = findViewById(R.id.empty);
        RecyclerView list = findViewById(R.id.words);
        list.setLayoutManager(new GridLayoutManager(this, 3));
        list.setAdapter(adapter);

        ChipGroup filter = findViewById(R.id.filter);
        addChip(filter, Pile.GOOD, R.string.filter_good, R.color.good, Color.WHITE);
        addChip(filter, Pile.PRACTICE, R.string.filter_practice, R.color.practice,
                ContextCompat.getColor(this, R.color.on_practice));
        addChip(filter, Pile.NEW, R.string.filter_new, R.color.new_word, Color.WHITE);
        refresh();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PILE, pile.name());
    }

    private void addChip(ChipGroup group, Pile value, int label, int color, int checkedText) {
        int count = 0;
        for (WordStats s : stats.values()) {
            if (s.pile() == value) {
                count++;
            }
        }
        Chip chip = new Chip(this);
        chip.setText(getString(label, count));
        chip.setCheckable(true);
        chip.setCheckedIconVisible(false);
        chip.setTextSize(16);
        int c = ContextCompat.getColor(this, color);
        int[][] states = {{android.R.attr.state_checked}, {}};
        chip.setChipBackgroundColor(new ColorStateList(states,
                new int[]{c, ContextCompat.getColor(this, R.color.surface)}));
        chip.setTextColor(new ColorStateList(states,
                new int[]{checkedText, ContextCompat.getColor(this, R.color.text_primary)}));
        chip.setChipStrokeColor(ColorStateList.valueOf(c));
        chip.setChipStrokeWidth(2 * getResources().getDisplayMetrics().density);
        group.addView(chip);
        chip.setChecked(value == pile);
        chip.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) {
                pile = value;
                refresh();
            }
        });
    }

    private void refresh() {
        List<WordStats> words = new ArrayList<>();
        for (WordStats s : stats.values()) {
            if (s.pile() == pile) {
                words.add(s);
            }
        }
        adapter.setWords(words);
        empty.setVisibility(words.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showDetail(WordStats s) {
        String text = s.count() == 0 ? getString(R.string.word_never)
                : getString(R.string.word_detail_text, s.count(), s.goodCount(),
                Format.seconds(s.recentAverageMillis()));
        Dialogs.builder(this)
                .setTitle(s.word)
                .setMessage(text)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private final class WordAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private List<WordStats> words = new ArrayList<>();

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        void setWords(List<WordStats> words) {
            this.words = words;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return words.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_word, parent, false);
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            WordStats s = words.get(position);
            View view = holder.itemView;
            ((TextView) view.findViewById(R.id.word)).setText(s.word);
            TextView detail = view.findViewById(R.id.detail);
            if (s.count() > 0) {
                detail.setText(getString(R.string.word_detail, Format.seconds(s.recentAverageMillis()), s.count()));
                detail.setVisibility(View.VISIBLE);
            } else {
                detail.setVisibility(View.GONE);
            }
            int color = s.pile() == Pile.GOOD ? R.color.good
                    : s.pile() == Pile.PRACTICE ? R.color.practice : R.color.new_word;
            ((MaterialCardView) view.findViewById(R.id.card))
                    .setStrokeColor(ContextCompat.getColor(WordsActivity.this, color));
            view.setOnClickListener(v -> showDetail(s));
        }
    }
}
