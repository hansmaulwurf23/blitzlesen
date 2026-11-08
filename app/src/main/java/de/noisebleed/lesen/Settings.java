package de.noisebleed.lesen;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** Einstellungen der App; bisher nur hell oder dunkel. */
public final class Settings {

    private static final String PREFS = "settings";
    private static final String KEY_DARK = "dark_theme";

    private Settings() {
    }

    /** Dunkel ist die Voreinstellung. */
    public static boolean isDark(Context context) {
        return prefs(context).getBoolean(KEY_DARK, true);
    }

    public static void setDark(Context context, boolean dark) {
        prefs(context).edit().putBoolean(KEY_DARK, dark).apply();
        applyTheme(context);
    }

    /** Erzeugt offene Activities neu, falls sich das Design ändert. */
    public static void applyTheme(Context context) {
        AppCompatDelegate.setDefaultNightMode(isDark(context)
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
