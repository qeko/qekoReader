package com.qeko.reader;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

public class ControlActivity {
    private final View panel;
    private final ReaderActivity activity;
    private final SharedPreferences preferences;

    private final Handler handler = new Handler();
    private Runnable exitRunnable;

    public ControlActivity(View panel, ReaderActivity activity) {
        this.panel = panel;
        this.activity = activity;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(activity);
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


    }

    private void setupSpeedControls() {
        RadioGroup speedGroup = panel.findViewById(R.id.radioSpeed);
        speedGroup.setOnCheckedChangeListener((group, checkedId) -> {
            float speed = 1.0f;
            if (checkedId == R.id.radio05x) speed = 0.5f;
            else if (checkedId == R.id.radio10x) speed = 1.0f;
            else if (checkedId == R.id.radio15x) speed = 1.5f;
            else if (checkedId == R.id.radio20x) speed = 2.0f;
            preferences.edit().putFloat("speechRate", speed).apply();
            activity.ttsManager.setSpeed(speed);
        });
    }

    private void setupFontSizeControls() {
        panel.findViewById(R.id.btnFontSizeDecrease).setOnClickListener(v -> activity.adjustFontSize(-2));
        panel.findViewById(R.id.btnFontSizeIncrease).setOnClickListener(v -> activity.adjustFontSize(+2));
    }

    private void setupThemeToggle() {
        panel.findViewById(R.id.btnThemeToggle).setOnClickListener(v -> {
            boolean isDark = preferences.getBoolean("isDark", false);
            preferences.edit().putBoolean("isDark", !isDark).apply();
            activity.updateTheme(!isDark);
        });
    }

    private void setupExitTimer() {
        RadioGroup timerGroup = panel.findViewById(R.id.radioExitTimer);
        TextView exitLabel = panel.findViewById(R.id.labelExitHint); // “分钟后退出”

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
            } else {
                exitLabel.setText("");
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
                preferences.edit().putString("fontName", fontName).apply();
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
