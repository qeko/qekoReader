package com.qeko.reader;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Handler;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.qeko.tts.TextToSpeechManager;

import java.util.List;

public class ControlActivity {
    private final View panel;
    private final ReaderActivity activity;
    private final Handler handler = new Handler();
    private Runnable exitRunnable;
    public ControlActivity(View panel, ReaderActivity activity) {
        this.panel = panel;
        this.activity = activity;
        setupControls();
    }

    private void setupControls() {
        setupSpeedControls();
        setupFontSizeControls();
        setupThemeToggle();
        setupExitTimer();
//        setupSimplifyConvert();
        setupBrightnessControl();
        setupFontSelector();
        OK();
    }

    private void setupSpeedControls() {
        RadioGroup speedGroup = panel.findViewById(R.id.radioSpeed);
        speedGroup.setOnCheckedChangeListener((group, checkedId) -> {
            float speed = 1.0f;
            if (checkedId == R.id.radio05x) speed = 0.5f;
            else if (checkedId == R.id.radio10x) speed = 1.0f;
            else if (checkedId == R.id.radio15x) speed = 1.5f;
            else if (checkedId == R.id.radio20x) speed = 2.0f;
//            preferences.edit().putFloat("speechRate", speed).apply();
            activity.appPreferences.setSpeechRate(speed);
            Log.d("TAG", "setupSpeedControls: "+activity.appPreferences.getSpeechRate());
            activity.ttsManager.setSpeed(speed);
        });
    }

    boolean changeSize = false;
    private void setupFontSizeControls() {
        panel.findViewById(R.id.btnFontSizeDecrease).setOnClickListener(v -> {activity.adjustFontSize(-2) ; changeSize = true;});
        panel.findViewById(R.id.btnFontSizeIncrease).setOnClickListener(v -> {activity.adjustFontSize(+2); changeSize = true;});
//        activity.setChangeFontSize(true);
    }

    private void setupThemeToggle() {
        panel.findViewById(R.id.btnThemeToggle).setOnClickListener(v -> {

            boolean isDark =activity.appPreferences.isDarkTheme();
            activity.appPreferences.setDarkTheme(isDark);
//            boolean isDark = preferences.getBoolean("isDark", false);
//            preferences.edit().putBoolean("isDark", !isDark).apply();
            activity.updateTheme(!isDark);
        });
    }

    private void OK() {
        panel.findViewById(R.id.btnOK).setOnClickListener(v -> {
            this.panel.setVisibility(View.GONE);
            setupControls();
            Log.d("TAG", "changeSize OK: "+changeSize);
            if(changeSize)
            {
                changeSize = false;
                new Thread(() -> {
                    activity.pageOffsets =  activity.buildPageOffsetsWithCache(activity.filePath, false);
                }).start();


                // 重新分页
                new Thread(() -> {
                    List<Integer> newOffsets = activity.buildPageOffsetsWithCache(activity.filePath, false);
                    int savedOffset = activity.appPreferences.getSavedOffset(activity.filePath);

                    // 找到 savedOffset 对应的新页码
                    int newPage = 0;
                    for (int i = 0; i < newOffsets.size(); i++) {
                        if (newOffsets.get(i) > savedOffset) {
                            newPage = Math.max(0, i - 1);
                            break;
                        }
                    }

                    int newSentence = 0; // 可以根据 offset 定位更精确的句子

                    int finalNewPage = newPage;
                    activity.runOnUiThread(() -> activity.loadPage(newOffsets, finalNewPage));
                }).start();

            }
        });
    }

    private void setupExitTimer() {
            RadioGroup timerGroup = panel.findViewById(R.id.radioExitTimer);
    //        TextView exitLabel = panel.findViewById(R.id.labelExitHint); // “分钟后退出”

            timerGroup.setOnCheckedChangeListener((group, checkedId) -> {
                int minutes = 0;
                if (checkedId == R.id.radio10min) minutes = 10;
                else if (checkedId == R.id.radio30min) minutes = 30;
                else if (checkedId == R.id.radio60min) minutes = 60;
                else if (checkedId == R.id.radio120min) minutes = 120;

                if (exitRunnable != null) handler.removeCallbacks(exitRunnable);

                if (minutes > 0) {
                    exitRunnable = () -> activity.finish();
                    handler.postDelayed(exitRunnable, minutes * 60 * 1000L);
    //                exitLabel.setText(minutes + " 分钟后退出");
                }
            });
    }

/*    private void setupSimplifyConvert() {
        panel.findViewById(R.id.btnConvert).setOnClickListener(v -> {
            activity.toggleSimplifiedTraditional();
        });
    }*/

    private void setupBrightnessControl() {
        SeekBar seekBar = panel.findViewById(R.id.seekBarBrightness);
        float brightness = activity.getWindow().getAttributes().screenBrightness;
        if (brightness < 0) brightness = 0.5f;
        seekBar.setProgress((int) (brightness * 100));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                float newBrightness = Math.max(0.01f, progress / 100f);
                WindowManager.LayoutParams layoutParams = activity.getWindow().getAttributes();
                layoutParams.screenBrightness = newBrightness;
                activity.getWindow().setAttributes(layoutParams);
            }

            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private void setupFontSelector() {
        panel.findViewById(R.id.btnFontSelect).setOnClickListener(v -> {
            final String[] fonts = {"默认字体", "宋体", "黑体", "楷体"};
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("选择字体");
            builder.setItems(fonts, (dialog, which) -> {
                String fontName = fonts[which];
//                preferences.edit().putString("fontName", fontName).apply();
                activity.appPreferences.setFontName(fontName);
                activity.setFont(fontName);
            });
            builder.show();
        });
    }

    public void show() {
        panel.setVisibility(View.VISIBLE);
    }

    public void hide() {
        panel.setVisibility(View.GONE);
    }

    public void toggleVisibility() {
        if (panel.getVisibility() == View.VISIBLE) hide(); else show();
    }

    public boolean isVisible() {
        return panel.getVisibility() == View.VISIBLE;
    }
}
