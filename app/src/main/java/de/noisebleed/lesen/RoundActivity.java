package de.noisebleed.lesen;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

import de.noisebleed.lesen.game.Round;
import de.noisebleed.lesen.stats.History;
import de.noisebleed.lesen.stats.HistoryStore;
import de.noisebleed.lesen.stats.RoundRecord;
import de.noisebleed.lesen.words.Picker;
import de.noisebleed.lesen.words.WordList;
import de.noisebleed.lesen.words.WordLists;

/**
 * Eine Minute Blitzlesen: zufällige Karten, nach jeder tippt man Grün ("kann ich") oder Gelb
 * ("hakt noch"). Die Karte fliegt auf ihren Stapel, die nächste springt herein.
 */
public class RoundActivity extends AppCompatActivity {

    public static final int SECONDS = 60;
    private static final long TICK_MS = 100;
    /**
     * So lange nach dem Aufdecken zählt kein Tipp: sonst bewertet ein versehentlicher Doppeltipp die
     * nächste Karte, bevor man sie überhaupt gesehen hat.
     */
    private static final long MIN_SHOW_MS = 350;

    private enum Phase { COUNTDOWN, PLAYING, ANIMATING, PAUSED, DONE }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private Round round;
    private WordList list;
    private Phase phase = Phase.PAUSED;
    private boolean abortDialogOpen;
    private long cardShownAt;

    private View card;
    private TextView word;
    private TextView timer;
    private TextView timeUp;
    private TextView countdown;
    private TextView countGood;
    private TextView countPractice;
    private LinearProgressIndicator timeBar;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateTimer();
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_round);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> confirmAbort());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmAbort();
            }
        });

        card = findViewById(R.id.card);
        word = findViewById(R.id.word);
        timer = findViewById(R.id.timer);
        timeUp = findViewById(R.id.time_up);
        countdown = findViewById(R.id.countdown);
        countGood = findViewById(R.id.count_good);
        countPractice = findViewById(R.id.count_practice);
        timeBar = findViewById(R.id.time_bar);

        findViewById(R.id.rate_good).setOnClickListener(v -> rate(v, true));
        findViewById(R.id.rate_practice).setOnClickListener(v -> rate(v, false));

        History history = HistoryStore.load(this);
        list = WordLists.current(this);
        Picker picker = new Picker(list.words, History.piles(history.wordStats(list.words)), random);
        round = new Round(SECONDS * 1000L, picker);
        updateCounts();
        updateTimer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (phase == Phase.PAUSED && !abortDialogOpen) {
            // Erst ein Countdown, auch nach einer Pause — sonst liest man schon, bevor die Uhr läuft.
            startCountdown();
        }
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseRound();
    }

    /** Uhr anhalten; weiter geht es danach immer mit einem Countdown. */
    private void pauseRound() {
        handler.removeCallbacksAndMessages(null);
        if (phase == Phase.COUNTDOWN) {
            countdown.animate().cancel();
            countdown.setVisibility(View.GONE);
            phase = Phase.PAUSED;
        } else if (phase == Phase.PLAYING || phase == Phase.ANIMATING) {
            card.animate().cancel();
            round.pause(SystemClock.elapsedRealtime());
            phase = Phase.PAUSED;
        }
    }

    // ---------------------------------------------------------------- Countdown

    private void startCountdown() {
        phase = Phase.COUNTDOWN;
        card.setVisibility(View.INVISIBLE);
        timeUp.setVisibility(View.INVISIBLE);
        countdownStep(3);
    }

    private void countdownStep(int n) {
        if (phase != Phase.COUNTDOWN) {
            return;
        }
        countdown.setVisibility(View.VISIBLE);
        countdown.setText(n > 0 ? String.valueOf(n) : getString(R.string.countdown_go));
        countdown.setScaleX(1.6f);
        countdown.setScaleY(1.6f);
        countdown.setAlpha(0f);
        countdown.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350)
                .setInterpolator(new OvershootInterpolator()).withEndAction(null).start();
        handler.postDelayed(() -> {
            if (n > 0) {
                countdownStep(n - 1);
            } else {
                countdown.setVisibility(View.GONE);
                play();
            }
        }, n > 0 ? 800 : 450);
    }

    private void play() {
        long now = SystemClock.elapsedRealtime();
        if (!round.isStarted()) {
            round.start(now);
        } else {
            round.resume(now);
            if (round.current() == null && !round.next(now)) {
                finishRound();
                return;
            }
        }
        phase = Phase.PLAYING;
        showCard(round.current());
    }

    // ---------------------------------------------------------------- Karten

    private void showCard(String text) {
        cardShownAt = SystemClock.elapsedRealtime();
        word.setText(text);
        card.setVisibility(View.VISIBLE);
        card.setTranslationX(0);
        card.setTranslationY(0);
        card.setRotation(random.nextFloat() * 6 - 3);
        card.setScaleX(0.6f);
        card.setScaleY(0.6f);
        card.setAlpha(0f);
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200)
                .setInterpolator(new OvershootInterpolator(1.5f)).withEndAction(null).start();
    }

    private void rate(View button, boolean good) {
        long now = SystemClock.elapsedRealtime();
        if (phase != Phase.PLAYING || round.current() == null || now - cardShownAt < MIN_SHOW_MS) {
            return;
        }
        round.answer(good, now);
        haptic(button);
        updateCounts();
        bounce(good ? countGood : countPractice);

        // Grün fliegt nach links unten auf den grünen Stapel, Gelb nach rechts.
        phase = Phase.ANIMATING;
        float direction = good ? -1 : 1;
        card.animate()
                .translationX(direction * card.getWidth())
                .translationY(card.getHeight() * 0.8f)
                .rotation(direction * 25)
                .scaleX(0.4f).scaleY(0.4f)
                .alpha(0f)
                .setDuration(220)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(this::nextCard)
                .start();
    }

    private void nextCard() {
        if (phase != Phase.ANIMATING) {
            return;
        }
        if (round.next(SystemClock.elapsedRealtime())) {
            phase = Phase.PLAYING;
            showCard(round.current());
        } else {
            finishRound();
        }
    }

    private void haptic(View view) {
        view.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void bounce(View view) {
        view.setScaleX(1.4f);
        view.setScaleY(1.4f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(250)
                .setInterpolator(new OvershootInterpolator(3f)).start();
    }

    private void updateCounts() {
        countGood.setText("✓ " + round.goodCount());
        countPractice.setText("↻ " + round.practiceCount());
    }

    private void updateTimer() {
        long now = SystemClock.elapsedRealtime();
        long remaining = round.isStarted() ? round.remaining(now) : round.getDurationMillis();
        timer.setText(Format.countdown(remaining));
        timeBar.setProgress((int) (1000 * remaining / round.getDurationMillis()));
        boolean up = round.isStarted() && round.isTimeUp(now);
        timer.setTextColor(ContextCompat.getColor(this, up ? R.color.practice : R.color.text_primary));
        if (up && phase == Phase.PLAYING && timeUp.getVisibility() != View.VISIBLE) {
            timeUp.setVisibility(View.VISIBLE);
            bounce(timeUp);
        }
    }

    // ---------------------------------------------------------------- Ende

    private void finishRound() {
        phase = Phase.DONE;
        handler.removeCallbacksAndMessages(null);
        RoundRecord record = new RoundRecord(UUID.randomUUID().toString(), System.currentTimeMillis(),
                SECONDS, list.id, new ArrayList<>(round.attempts()));
        try {
            HistoryStore.add(this, record);
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.result_save_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        startActivity(new Intent(this, ResultActivity.class).putExtra(ResultActivity.EXTRA_ROUND, record.id));
        finish();
    }

    private void confirmAbort() {
        if (phase == Phase.DONE) {
            return;
        }
        // Während der Rückfrage steht die Uhr; danach geht es mit Countdown weiter.
        pauseRound();
        card.setVisibility(View.INVISIBLE);
        abortDialogOpen = true;
        Dialogs.builder(this)
                .setTitle(R.string.abort_title)
                .setMessage(R.string.abort_text)
                .setPositiveButton(R.string.abort_confirm, (d, w) -> {
                    phase = Phase.DONE;
                    finish();
                })
                .setNegativeButton(R.string.abort_continue, null)
                .setOnDismissListener(d -> {
                    abortDialogOpen = false;
                    if (phase == Phase.PAUSED) {
                        startCountdown();
                        handler.removeCallbacks(ticker);
                        handler.post(ticker);
                    }
                })
                .show();
    }
}
