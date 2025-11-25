package com.qeko.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.util.Log;
import android.util.TypedValue;
import android.view.WindowManager;

import java.io.File;

public class AppPreferences {

    private static final String PREF_NAME = "reader_prefs";
    private final SharedPreferences preferences;
    private static final String PREF_SPEECH_RATE = "speechRate";

    public AppPreferences(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ====== 保存阅读进度 ======

    public void saveProgress(String filePath, long offset, int sentenceIndex, int page) {
        Log.d("TAG", "saveProgress: "+filePath);
        preferences.edit()
                .putLong(filePath + "_offset", offset)
                .putInt(filePath + "_sentence", sentenceIndex)
                .putInt(filePath + "_page", page)
                .apply();
    }

    public long getSavedOffset(String filePath) {
        Log.d("TAG", "getSavedOffset: "+filePath);
        return preferences.getLong(filePath + "_offset", 0);
    }

    public int getSavedSentenceIndex(String filePath) {
        Log.d("TAG", "getSavedSentenceIndex: "+filePath);
        return preferences.getInt(filePath + "_sentence", 0);
    }

    public int getSavedPage(String filePath) {
        Log.d("TAG", "getSavedPage: "+filePath);
        return preferences.getInt(filePath + "_page", 0);
    }


    /** 读取最后阅读的页码，默认返回0 */
    public int getLastPageIndex(String filePath) {
        try {

            int index = preferences.getInt(filePath + "_lastPage", 1);

            return index;
        } catch (Exception e) {

            return 0;
        }
    }

    // ====== 保存语速 ======

    public float getSpeechRate() {
        return preferences.getFloat(PREF_SPEECH_RATE, 1.0f);
    }


    public void setSpeechRate(float rate) {
        preferences.edit().putFloat(PREF_SPEECH_RATE, rate).apply();
    }





    // =================== PDF 增量抽取页码 ===================
    /**
     * 获取某个 PDF 文件（通过 keyBase 标识）已抽取的最后一页（0 表示未抽取）
     */
    public int getPdfExtractedPage(String keyBase) {
        return preferences.getInt(keyBase + "_pdf_extracted_page", 0);
    }

    /**
     * 保存某个 PDF 文件（通过 keyBase 标识）已抽取的最后一页
     */
    public void savePdfExtractedPage(String keyBase, int page) {
        preferences.edit().putInt(keyBase + "_pdf_extracted_page", page).apply();
    }


    /**
     * 获取指定 EPUB 文件的已抽取章节索引
     * @param keyBase 文件路径或唯一标识
     * @return 已抽取的章节数量（0 表示未抽取）
     */
    public int getEpubExtractedChapter(String keyBase) {
        if (keyBase == null) return 0;
        return preferences.getInt(keyBase + "_epub_chapter", 0);
    }

    /**
     * 保存指定 EPUB 文件的已抽取章节索引
     * @param keyBase 文件路径或唯一标识
     * @param chapterIndex 已抽取的章节索引（下一次从此索引开始）
     */
    public void saveEpubExtractedChapter(String keyBase, int chapterIndex) {
        if (keyBase == null) return;
        preferences.edit().putInt(keyBase + "_epub_chapter", chapterIndex).apply();
    }


    // ========== 亮度 ==========
    public void saveBrightness(float value) {
        preferences.edit().putFloat("brightness", value).apply();
    }
    public float getBrightness() {
        return preferences.getFloat("brightness", 1.0f);
    }

    // ========== 反白 ==========
    public void saveInvertMode(boolean value) {
        preferences.edit().putBoolean("invert_mode", value).apply();
    }
    public boolean isInvertMode() {
        return preferences.getBoolean("invert_mode", false);
    }



    // AppPreferences.java 中
    public void saveTextSizeSp( float textSizeSp) {
        Log.d("", "saveTextSizeSp:= "+textSizeSp);
        preferences.edit().putFloat( "_text_size_sp", textSizeSp).apply();
    }
    public float getTextSizeSp(  float defaultSp) {
        return preferences.getFloat("_text_size_sp", defaultSp);
    }

    // ========== 行距 ==========
    public void saveLineSpacing(float value) {
        preferences.edit().putFloat("line_spacing", value).apply();
    }
    public float getLineSpacing() {
        return preferences.getFloat("line_spacing", 1.4f);
    }

    // ========== 字体 ==========
    public void saveFontPath(String path) {
        preferences.edit().putString("font_path", path).apply();
    }
    public String getFontPath() {
        return preferences.getString("font_path", "fonts/SimsunExtG.ttf");
    }

}
