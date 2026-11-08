package de.noisebleed.lesen;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

/** Füllt einen Kartenstapel aus {@code view_pile.xml}: grün oder gelb, mit Zahl und Beschriftung. */
final class PileView {

    private PileView() {
    }

    static void bind(View pile, boolean good, int count, View.OnClickListener onClick) {
        Context context = pile.getContext();
        int front = ContextCompat.getColor(context, good ? R.color.good : R.color.practice);
        int back = ContextCompat.getColor(context, good ? R.color.good_back : R.color.practice_back);
        int text = good ? 0xFFFFFFFF : ContextCompat.getColor(context, R.color.on_practice);

        ((MaterialCardView) pile.findViewById(R.id.front)).setCardBackgroundColor(front);
        ((MaterialCardView) pile.findViewById(R.id.back1)).setCardBackgroundColor(back);
        ((MaterialCardView) pile.findViewById(R.id.back2)).setCardBackgroundColor(back);
        // Ein leerer Stapel ist nur eine Karte, mit jedem Wort wächst er bis zu drei.
        pile.findViewById(R.id.back1).setVisibility(count >= 1 ? View.VISIBLE : View.INVISIBLE);
        pile.findViewById(R.id.back2).setVisibility(count >= 10 ? View.VISIBLE : View.INVISIBLE);

        TextView number = pile.findViewById(R.id.count);
        number.setText(String.valueOf(count));
        number.setTextColor(text);
        TextView label = pile.findViewById(R.id.label);
        label.setText(good ? R.string.pile_good : R.string.pile_practice);
        label.setTextColor(text);

        View card = pile.findViewById(R.id.front);
        card.setOnClickListener(onClick);
        card.setClickable(onClick != null);
    }
}
