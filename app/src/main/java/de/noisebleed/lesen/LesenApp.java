package de.noisebleed.lesen;

import android.app.Application;

/** Setzt das gewählte Design, bevor die erste Activity startet. */
public class LesenApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Settings.applyTheme(this);
    }
}
