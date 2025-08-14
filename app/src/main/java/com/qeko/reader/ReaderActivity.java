package com.qeko.reader;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.text.StaticLayout;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.qeko.tts.TextToSpeechManager;
import com.qeko.utils.AppPreferences;
import com.qeko.utils.FileUtils;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
//import com.ibm.icu.text.Transliterator;

    public class ReaderActivity extends AppCompatActivity {
    private AppPreferences appPreferences;
    public TextView textView;
    private Button btnTTS;
    private SeekBar seekBar;
    private TextView pageInfo;
    public TextToSpeechManager ttsManager;
    private ControlActivity controlActivity;

    private boolean isSimplified = true;

    private String fullText = "";
    private List<Integer> pageOffsets = new ArrayList<>();
    public int currentPage = 0, totalPages = 0;
    private String[] currentSentences;
    private int sentenceIndex = 0;
    private boolean isSpeaking = false;

    private float speechRate;
    private float fontSize;
    private boolean isInitialLoad = true;
    private String filePath;
    private Dialog loadingDialog;
        private static final String FONT_PATH = "fonts/SimsunExtG.ttf";
    private int lastPage ;
    private  int lastSentence  ;
    private float lineSpacingMultiplier = 1.5f; // 示例值，也可以存储为用户偏好

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        showLoadingDialog();

        appPreferences = new AppPreferences(this);

        textView = findViewById(R.id.textContent);
        btnTTS = findViewById(R.id.btnTTS);
        seekBar = findViewById(R.id.pageSeekBar);
        pageInfo = findViewById(R.id.pageInfo);

        speechRate = appPreferences.getSpeechRate();
        fontSize = appPreferences.getFontSize();

//        if (null == ttsManager)   ttsManager = new TextToSpeechManager(this, speechRate, this::onTtsDone);

        controlActivity = new ControlActivity(findViewById(R.id.controlPanel), this);

        textView.setTextSize(fontSize);
//

        textView.setLineSpacing(5, lineSpacingMultiplier);

        restoreUserSettings();

        filePath = getIntent().getStringExtra("filePath");



        if (filePath != null && new File(filePath).exists()) {
            loadText(filePath);
            textView.post(() -> {
                new Thread(() -> {
                    buildPageOffsets();
                    runOnUiThread(() -> {
                        dismissLoadingDialog();
                        loadPage(currentPage);
                    });
                }).start();
            });
        }

        setupSeekBar();
        setupTouchControl();
        btnTTS.setOnClickListener(v -> toggleSpeaking());
    }


    private void restoreUserSettings() {
        speechRate  = appPreferences.getSpeechRate();
        textView.setTextSize(fontSize);
        updateTheme(appPreferences.isDarkTheme());
        setFont(appPreferences.getFontName());

        lastPage = appPreferences.getLastPage();
        lastSentence = appPreferences.getLastSentence();
    }

    private void showLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null);
        builder.setView(view);
        builder.setCancelable(false);
        loadingDialog = builder.create();
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setDimAmount(0.5f);
            loadingDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        loadingDialog.show();
    }

    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }


        private void loadText(String path) {
            try {
                File file = new File(path);
                String textFilePath;

                if (path.toLowerCase().endsWith(".pdf")) {
                    // 处理 PDF -> .pdftxt
                    textFilePath = path + ".pdftxt";
                    File txtFile = new File(textFilePath);

                    if (!txtFile.exists()) {

                        Toast.makeText(this, "首次打开PDF，预处理需要一点时间 " , Toast.LENGTH_SHORT).show();
                        FileUtils.extractTextFromPdf(file, this);
                    }
                } else {
                    // 直接使用原始文件
                    textFilePath = path;
                }

                fullText = readFileToString(new File(textFilePath));

            } catch (Exception e) {
                Toast.makeText(this, "读取失败", Toast.LENGTH_SHORT).show();
                fullText = "";
            }

            currentPage = lastPage;
            sentenceIndex = lastSentence;
        }

        /** 将文件完整读取为字符串（自动检测编码） */
        private String readFileToString(File file) throws IOException {
            Charset charset = detectEncoding(file);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            return sb.toString();
        }


     public void buildPageOffsets() {
        pageOffsets.clear();

        int viewWidth = textView.getWidth() - textView.getPaddingLeft() - textView.getPaddingRight();
        int viewHeight = textView.getHeight() - textView.getPaddingTop() - textView.getPaddingBottom();
        if (viewWidth <= 0 || viewHeight <= 0) return;
        viewHeight = viewHeight - 680;
        TextPaint paint = textView.getPaint();
        int start = 0;
        int textLength = fullText.length();
        pageOffsets.add(start);

        while (start < textLength) {
            int low = start + 1;
            int high = Math.min(textLength, start + 2000);
            int fitPos = start + 1;

            while (low <= high) {
                int mid = (low + high) / 2;
                String sub = fullText.substring(start, mid);
                StaticLayout layout = android.text.StaticLayout.Builder.obtain(sub, 0, sub.length(), paint, viewWidth)
                        .setLineSpacing(0f, 1.2f).setIncludePad(false).build();
                if (layout.getHeight() <= viewHeight) {
                    fitPos = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }

            if (fitPos <= start) break;
            pageOffsets.add(fitPos);
            start = fitPos;
        }

        totalPages = pageOffsets.size() - 1;
        seekBar.setMax(Math.max(totalPages, 1));
    }

    private Charset detectEncoding(File file) {
        byte[] buf = new byte[4096];
        try (FileInputStream fis = new FileInputStream(file)) {
            UniversalDetector detector = new UniversalDetector(null);
            int nread;
            while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, nread);
            }
            detector.dataEnd();
            String encoding = detector.getDetectedCharset();
            detector.reset();
            if (encoding != null) {
                return Charset.forName(encoding);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Charset.forName("GBK");
    }


    private void loadPage(int page) {
        Log.d("loadPage", "loadPage ");
        if (page < 0 || page >= totalPages) return;

/*      int start = pageOffsets.get(page);
        int end = pageOffsets.get(page + 1);*/
        int start = pageOffsets.get(page);
        int end = (page + 1 < pageOffsets.size()) ? pageOffsets.get(page + 1) : fullText.length();

        // 防御：确保 start 和 end 合法
        if (start < 0) start = 0;
        if (end > fullText.length()) end = fullText.length();
        if (end < start) end = start;

        String pageText = fullText.substring(start, end);
        Log.d("TAG", "loadPage:pageText "+pageText);
        currentSentences = pageText.split("(?<=[.,，?!。！？])");

        if (isInitialLoad && page == currentPage) {
            int lastSentence = PreferenceManager.getDefaultSharedPreferences(this).getInt("lastSentence", 0);
            sentenceIndex = Math.min(lastSentence, currentSentences.length - 1);
            isInitialLoad = false;  // 🔴 防止后续翻页继续恢复
        } else {
            sentenceIndex = 0;
        }

        highlightSentence(-1);
        currentPage = page;
        seekBar.setProgress(page);
        updatePageInfo();



        new Thread(() -> {
            runOnUiThread(() -> {
                if (null == ttsManager)
                {
                    ttsManager = new TextToSpeechManager(this,  this::onTtsDone);
                    ttsManager.setSpeed(speechRate);
                    //自动点击
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // 模拟点击事件
                            if (0 == TextToSpeech.SUCCESS) {
                                toggleSpeaking();
                            }
//                speakCurrentPage();
                        }
                    }, 2000); // 1秒后执行
                }
            });
        }).start();


    }


    private void toggleSpeaking() {
        if (isSpeaking) {
            ttsManager.stop();
            isSpeaking = false;
            btnTTS.setText("▶️");
        } else {
            controlActivity.hide();
            speakCurrentPage();
        }
    }

    private void speakCurrentPage() {

        isSpeaking = true;
        btnTTS.setText("⏸️");
        speakNextSentence();
    }


    private void speakNextSentence() {
        if(currentSentences!=null)
        {
            if (sentenceIndex >= currentSentences.length) {
                // 当前页读完，自动翻页朗读下一页
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    loadPage(currentPage);
                    speakCurrentPage();
                } else {
                    // 读完所有页
                    isSpeaking = false;
                    btnTTS.setText("▶️");
                    highlightSentence(-1);
                }
        }

        String sentence = currentSentences[sentenceIndex];
        highlightSentence(sentenceIndex);

        // 清理多余特殊字符和引号，防止TTS读错
        sentence = sentence.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9\\s]{3,}", "");
        sentence = sentence.replaceAll("[\"“”]", "");
        sentence = sentence.replaceAll("\\.", "");

        ttsManager.speak(sentence);
            return;
        }
    }


    private List<String> splitByLength(String text, int maxLen) {
        List<String> chunks = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            int end = Math.min(index + maxLen, text.length());
            chunks.add(text.substring(index, end));
            index = end;
        }
        return chunks;
    }


    private void onTtsDone() {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putInt("lastPage", currentPage)
                .putInt("lastSentence", sentenceIndex)
                .apply();

        sentenceIndex++;
        speakNextSentence();
    }

    private void highlightSentence(int index) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (int i = 0; i < currentSentences.length; i++) {
            int start = builder.length();
            builder.append(currentSentences[i]);
            int end = builder.length();

            if (i == index) {
                builder.setSpan(new ForegroundColorSpan(Color.RED), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (i < index) {
                builder.setSpan(new ForegroundColorSpan(Color.GRAY), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        textView.setText(builder);
    }

    private void setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    if (isSpeaking) {
                        ttsManager.stop();
                        isSpeaking = false;
                        btnTTS.setText("▶️");
                    }
                    currentPage = p;
                    loadPage(p);
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updatePageInfo() {
        if (pageInfo != null) {
            float percent = totalPages > 0 ? (currentPage + 1) * 100f / totalPages : 0f;
            String text = String.format("%d/%d  %.0f%%", currentPage + 1, totalPages, percent);
            pageInfo.setText(text);
        }

        if (seekBar.getProgress() != currentPage) {
            seekBar.setProgress(currentPage);
        }
    }

    private void setupTouchControl() {
        textView.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                float x = e.getX();
                float width = textView.getWidth();

                if (isSpeaking) return true;

                if (x < width / 3f) {
                    if (currentPage > 0) {
                        currentPage--;
                        loadPage(currentPage);
                    }
                } else if (x > width * 2 / 3f) {
                    if (currentPage < totalPages - 1) {
                        currentPage++;
                        loadPage(currentPage);
                    }
                } else {
                    controlActivity.toggleVisibility();
                }
            }
            return true;
        });
    }


    @Override
    protected void onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString("lastFilePath", getIntent().getStringExtra("filePath"))
                .putInt("lastPage", currentPage)
                .putInt("lastSentence", sentenceIndex)
                .apply();

        if (ttsManager != null) {
            ttsManager.stop();
            ttsManager.shutdown();
        }
        super.onDestroy();
    }


    public void adjustFontSize(float delta) {
        float newSize = textView.getTextSize() / getResources().getDisplayMetrics().scaledDensity + delta;
        textView.setTextSize(newSize);
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putFloat("fontSize", newSize).apply();

/*        textView.postDelayed(() -> {
            buildPageOffsets();
            loadPage(currentPage);
        }, 200);*/
    }

    public void updateTheme(boolean isDark) {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean("isDark", isDark).apply();

        int bg = isDark ? Color.BLACK : Color.WHITE;
        int fg = isDark ? Color.LTGRAY : Color.DKGRAY;
        textView.setBackgroundColor(bg);
        textView.setTextColor(fg);
    }



    public void setFont(String fontName) {
        Typeface typeface;

        switch (fontName) {
            case "宋体":
                typeface = Typeface.create("serif", Typeface.NORMAL);
                break;
            case "黑体":
                typeface = Typeface.create("sans-serif", Typeface.NORMAL);
                break;
            case "楷体":
                typeface = Typeface.create("cursive", Typeface.NORMAL); // Android 不一定内置楷体
                break;

/*            case "微软雅黑":
                // 微软雅黑可能在 Android 中不存在，你可以将字体文件放到 assets/fonts/ 目录中
                try {
                    typeface = Typeface.createFromAsset(getAssets(), "fonts/microsoft_yahei.ttf");
                } catch (Exception e) {
                    typeface = Typeface.DEFAULT;
                    Toast.makeText(this, "未找到微软雅黑字体，已切换为默认", Toast.LENGTH_SHORT).show();
                }
                break;*/
            default:
                typeface = Typeface.DEFAULT;
                break;
        }

        textView.setTypeface(typeface);
    }

}