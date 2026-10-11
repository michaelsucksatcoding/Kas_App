package com.example.kas_app;

import android.app.Application;

/**
 * Applies the saved dark-mode preference at process start.
 *
 * <p>This has to happen here rather than in an Activity's {@code onCreate}: an
 * AppCompatActivity resolves its night-mode configuration override in
 * {@code attachBaseContext}, which runs <em>before</em> {@code onCreate}. Calling
 * {@code AppCompatDelegate.setDefaultNightMode} from {@code onCreate} is therefore
 * too late for that Activity, which then falls back to the device's system night
 * mode. That made the launcher screen paint its background from the Material 3
 * dark palette while every later screen used the saved preference.
 */
public class KasApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        MainActivity.applySavedTheme(this);
    }
}