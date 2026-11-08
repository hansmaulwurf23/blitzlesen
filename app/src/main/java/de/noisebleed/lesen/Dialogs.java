package de.noisebleed.lesen;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

/**
 * Dialoge mit runden Ecken und dezentem Rand, passend zu den Karten der App.
 */
public final class Dialogs {

    private Dialogs() {
    }

    public static MaterialAlertDialogBuilder builder(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        MaterialShapeDrawable background = new MaterialShapeDrawable(
                ShapeAppearanceModel.builder().setAllCornerSizes(24 * density).build());
        background.setFillColor(ContextCompat.getColorStateList(context, R.color.dialog_bg));
        background.setStroke(1.5f * density, ContextCompat.getColor(context, R.color.dialog_border));
        return new MaterialAlertDialogBuilder(context).setBackground(background);
    }
}
