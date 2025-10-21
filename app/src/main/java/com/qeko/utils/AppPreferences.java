package com.qeko.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.qeko.reader.ReaderActivity;


public class AppPreferences {

    private static final String PREF_LAST_FILE_PATH = "lastFilePath";
    private static final String PREF_LAST_PAGE = "lastPage";
    private static final String PREF_LAST_SENTENCE = "lastSentence";
    private static final String PREF_SPEECH_RATE = "speechRate";
    private static final String PREF_FONT_SIZE = "fontSize";
    private static final String PREF_IS_DARK = "isDark"; // 保留一个
    private static final String PREF_EXIT_TIME = "exitTime";
    private static final String PREF_FONT_NAME = "fontName";
    private static final String PREF_SIMPLIFIED = "simplified"; // true简体，false繁体
    private static final String PREF_BRIGHTNESS = "brightness";

    private static final String PREFS_NAME = "reader_prefs";

    private final SharedPreferences preferences;
    private static final String KEY_TOTAL_PAGES = "total_pages";
    private static final String KEY_MAX_CHARS = "max_chars_per_page";

    public AppPreferences(ReaderActivity context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ---------- 每本书独立存储 ----------
    public void saveTextLength(String filePath, int length) {
        preferences.edit().putInt("textLength_" + filePath.hashCode(), length).apply();
    }

    public int getTextLength(String filePath) {
        return preferences.getInt("textLength_" + filePath.hashCode(), 0);
    }

    public void savePageCharCount(String filePath, int count) {
        preferences.edit().putInt("pageCharCount_" + filePath.hashCode(), count).apply();
    }

    public int getPageCharCount(String filePath) {
        return preferences.getInt("pageCharCount_" + filePath.hashCode(), 0);
    }

    public void saveCurrentPage(String filePath, int page) {
        preferences.edit().putInt("currentPage_" + filePath.hashCode(), page).apply();
    }

    public int getCurrentPage(String filePath) {
        return preferences.getInt("currentPage_" + filePath.hashCode(), 0);
    }

    public void saveSentenceIndex(String filePath, int index) {
        preferences.edit().putInt("sentenceIndex_" + filePath.hashCode(), index).apply();
    }

    public int getSentenceIndex(String filePath) {
        return preferences.getInt("sentenceIndex_" + filePath.hashCode(), 0);
    }

    public void saveProgress(String filePath, int page, int sentence, int charOffset) {
        preferences.edit()
                .putInt(filePath + "_page", page)
                .putInt(filePath + "_sentence", sentence)
                .putInt(filePath + "_offset", charOffset)
                .apply();
    }

    public int getSavedOffset(String filePath) {
        return preferences.getInt(filePath + "_offset", 0);
    }

    // ---------- 全局设置 ----------
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

    // ---------- Max Chars Per Page ----------
    public int getMaxCharsPerPage() { return preferences.getInt(KEY_MAX_CHARS, 0); }
    public void setMaxCharsPerPage(int maxChars) { preferences.edit().putInt(KEY_MAX_CHARS, maxChars).apply(); }

// ---------- Total Pages ----------
     public int getTotalPages() { return preferences.getInt(KEY_TOTAL_PAGES, 0); }
     public void setTotalPages(int totalPages) { preferences.edit().putInt(KEY_TOTAL_PAGES, totalPages).apply(); }
}
