package com.giftgram.messenger;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * GiftGram — приватная сборка мессенджера.
 * Хранит пользовательские настройки (тема) и применяет их до старта UI.
 */
public class GiftGramApp extends Application {

    public static final String PREFS = "giftgram_prefs";
    public static final String KEY_DARK = "dark_theme";
    public static final String KEY_LAST_TAB = "last_tab";

    private static SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        applyTheme(isDark());
    }

    public static SharedPreferences prefs() {
        return prefs;
    }

    public static boolean isDark() {
        return prefs == null || prefs.getBoolean(KEY_DARK, true);
    }

    public static void setDark(boolean dark) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_DARK, dark).apply();
        }
        applyTheme(dark);
    }

    private static void applyTheme(boolean dark) {
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }
}
