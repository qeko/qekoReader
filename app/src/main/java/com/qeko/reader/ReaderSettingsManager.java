
package com.qeko.reader;


import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.qeko.tts.TextToSpeechManager;
import com.qeko.utils.AppPreferences;


public class ReaderSettingsManager {

    private final Activity activity;
    private final Window window;

    // UI 控件
    private TextView tvFontSizeValue;
    private TextView tvLineSpacingValue;
    private TextView tvBrightnessValue;
    private TextView tvSpeechRateValue;

    private float currentFontSize = 16f;
    private float currentLineSpacing = 1.4f;
    private float currentBrightness = 0.5f;
    private float currentSpeechRate = 1.0f;

    public ReaderSettingsManager(Activity activity) {
        this.activity = activity;
        this.window = activity.getWindow();
    }

    public void initViews() {
        tvFontSizeValue     = activity.findViewById(R.id.tvFontSizeValue);
        tvLineSpacingValue  = activity.findViewById(R.id.tvLineSpacingValue);
        tvBrightnessValue   = activity.findViewById(R.id.tvBrightnessValue);
        tvSpeechRateValue   = activity.findViewById(R.id.tvSpeechRateValue);

        updateAllDisplay();
    }

    private void updateAllDisplay() {
        if (tvFontSizeValue != null)
            tvFontSizeValue.setText(String.valueOf((int) currentFontSize));

        if (tvLineSpacingValue != null)
            tvLineSpacingValue.setText(String.format("%.1f", currentLineSpacing));

        if (tvBrightnessValue != null)
            tvBrightnessValue.setText(String.valueOf((int)(currentBrightness * 100)));

        if (tvSpeechRateValue != null)
            tvSpeechRateValue.setText(String.format("%.1f", currentSpeechRate));
    }

    // ---------------- 字号 ----------------
    public void changeFontSize(float deltaSp) {
        currentFontSize = Math.max(8f, currentFontSize + deltaSp);
        tvFontSizeValue.setText(String.valueOf((int) currentFontSize));

        change=true;
    }

    // ---------------- 行距 ----------------
    public void changeLineSpacing(float delta) {
        currentLineSpacing = Math.max(1.0f, currentLineSpacing + delta);
        tvLineSpacingValue.setText(String.format("%.1f", currentLineSpacing));
        change=true;
    }

    // ---------------- 亮度 ----------------
    public void changeBrightness(float delta) {
        currentBrightness = Math.min(1.0f, Math.max(0.05f, currentBrightness + delta));

        WindowManager.LayoutParams lp = window.getAttributes();
        lp.screenBrightness = currentBrightness;
        window.setAttributes(lp);

        tvBrightnessValue.setText(String.valueOf((int)(currentBrightness * 100)));
    }

    // ---------------- 语速 ----------------
/*    public void changeSpeechRate(float delta) {
        currentSpeechRate = Math.max(0.5f, Math.min(2.0f, currentSpeechRate + delta));
        tvSpeechRateValue.setText(String.format("%.1f", currentSpeechRate));
    }*/
    public void changeSpeechRate(float delta) {
        // 加减速度后四舍五入到最近的 0.1
        currentSpeechRate = currentSpeechRate + delta;
        currentSpeechRate = Math.max(0.5f, Math.min(2.0f, currentSpeechRate));
        currentSpeechRate = (float)Math.round(currentSpeechRate * 10) / 10f;

//        tvSpeechRateValue.setText(String.format("%.1f", currentSpeechRate));
        tvSpeechRateValue.setText(String.format("%.1f", currentSpeechRate));

    }

    private  boolean change=false;
    public boolean getChange(){return change;}

    public void setChange(boolean newchange){change = newchange;}


    // getters for ReaderActivity
    public float getFontSize() { return currentFontSize; }
    public float getLineSpacing() { return currentLineSpacing; }
    public float getBrightness() { return currentBrightness; }
    public float getSpeechRate() { return currentSpeechRate; }




}
