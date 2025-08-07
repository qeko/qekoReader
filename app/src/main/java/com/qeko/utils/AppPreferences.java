package com.qeko.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class AppPreferences {

    private static final String PREF_LAST_FILE_PATH = "lastFilePath";
    private static final String PREF_LAST_PAGE = "lastPage";
    private static final String PREF_LAST_SENTENCE = "lastSentence";
    private static final String PREF_SPEECH_RATE = "speechRate";
    private static final String PREF_FONT_SIZE = "fontSize";
    private static final String PREF_IS_DARK = "isDark";
    private static final String PREF_EXIT_TIME = "exitTime";
    private static final String PREF_FONT_NAME = "fontName";
    private static final String PREF_SIMPLIFIED = "simplified"; // true简体，false繁体
    private static final String PREF_BRIGHTNESS = "brightness";

    private final SharedPreferences preferences;

    public AppPreferences(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public String getLastFilePath() {
        return preferences.getString(PREF_LAST_FILE_PATH, null);
    }

    public void setLastFilePath(String path) {
        preferences.edit().putString(PREF_LAST_FILE_PATH, path).apply();
    }

    public int getLastPage() {
        return preferences.getInt(PREF_LAST_PAGE, 0);
    }

    public void setLastPage(int page) {
        preferences.edit().putInt(PREF_LAST_PAGE, page).apply();
    }

    public int getLastSentence() {
        return preferences.getInt(PREF_LAST_SENTENCE, 0);
    }

    public void setLastSentence(int sentence) {
        preferences.edit().putInt(PREF_LAST_SENTENCE, sentence).apply();
    }

    public float getSpeechRate() {
        return preferences.getFloat(PREF_SPEECH_RATE, 1.0f);
    }

    public void setSpeechRate(float rate) {
        preferences.edit().putFloat(PREF_SPEECH_RATE, rate).apply();
    }

    public float getFontSize() {
        return preferences.getFloat(PREF_FONT_SIZE, 18f);
    }

    public void setFontSize(float size) {
        preferences.edit().putFloat(PREF_FONT_SIZE, size).apply();
    }

    public boolean isDarkTheme() {
        return preferences.getBoolean(PREF_IS_DARK, false);
    }

    public void setDarkTheme(boolean isDark) {
        preferences.edit().putBoolean(PREF_IS_DARK, isDark).apply();
    }

    public long getExitTime() {
        return preferences.getLong(PREF_EXIT_TIME, 0L);
    }

    public void setExitTime(long timestamp) {
        preferences.edit().putLong(PREF_EXIT_TIME, timestamp).apply();
    }

    public String getFontName() {
        return preferences.getString(PREF_FONT_NAME, "默认字体");
    }

    public void setFontName(String fontName) {
        preferences.edit().putString(PREF_FONT_NAME, fontName).apply();
    }

    public boolean isSimplified() {
        return preferences.getBoolean(PREF_SIMPLIFIED, true);
    }

    public void setSimplified(boolean simplified) {
        preferences.edit().putBoolean(PREF_SIMPLIFIED, simplified).apply();
    }

    public float getBrightness() {
        return preferences.getFloat(PREF_BRIGHTNESS, 1.0f);
    }

    public void setBrightness(float brightness) {
        preferences.edit().putFloat(PREF_BRIGHTNESS, brightness).apply();
    }

}
