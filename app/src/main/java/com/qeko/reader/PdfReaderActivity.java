
package com.qeko.reader;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.qeko.tts.TextToSpeechManager;

import com.qeko.utils.AppPreferences;
import com.qeko.utils.FileUtils;
import com.qeko.utils.SentenceSplitter;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class PdfReaderActivity extends AppCompatActivity {

    private TextView tvContent, pageInfo;
    private Button btnTTS;
    private SeekBar pageSeekBar;
    private ScrollView scrollView;

    private List<String> pages;
    private List<String> sentences;
    private int currentPage = 0;
    private int currentSentenceIndex = 0;
    private TextToSpeechManager ttsManager;
    private AppPreferences appPreferences;

    private boolean isReading = false;
    private String fileContent = "";
    private String pdfFilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_reader);

        scrollView = findViewById(R.id.scrollView);
        tvContent = findViewById(R.id.tvContent);
        btnTTS = findViewById(R.id.btnTTS);
        pageInfo = findViewById(R.id.pageInfo);
        pageSeekBar = findViewById(R.id.pageSeekBar);

        appPreferences = new AppPreferences(this);
        pdfFilePath = getIntent().getStringExtra("filePath");
        float speechRate = appPreferences.getSpeechRate();

        ttsManager = new TextToSpeechManager(this, this::onTtsDone);
        ttsManager.setSpeed(speechRate);

        loadPdfText();
        setupUI();
    }

    private void loadPdfText() {
        try {
            File file = new File(pdfFilePath);

//            fileContent = FileUtils.extractTextFromPdf(file, this, "fonts/SimsunExtG.ttf"); // 确保字体文件存在
            fileContent = FileUtils.extractTextFromPdf(file, this, "fonts/SimsunExtG.ttf"); // 确保字体文件存在
            Log.d("DEBUG", "content bytes=" + Arrays.toString(fileContent.getBytes()));
            pages = SentenceSplitter.splitToPages(fileContent, 300); // 可自适应
            updatePage(currentPage);
        } catch (Exception e) {
            Toast.makeText(this, "加载PDF失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupUI() {
        btnTTS.setOnClickListener(v -> toggleTTS());
        pageSeekBar.setMax(pages.size() - 1);
        pageSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) updatePage(progress);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        tvContent.setOnClickListener(v -> {
            float x = v.getX();
            float screenWidth = v.getWidth();
            if (x > screenWidth * 0.7f) {
                nextPage();
            } else if (x < screenWidth * 0.3f) {
                previousPage();
            }
        });
    }

    private void toggleTTS() {
        if (isReading) {
            ttsManager.stop();
            isReading = false;
            btnTTS.setText("▶️️");
        } else {
            startReadingPage();
            isReading = true;
            btnTTS.setText("⏸️");
        }
    }

    private void updatePage(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pages.size()) return;

        currentPage = pageIndex;
        String pageText = pages.get(pageIndex);
        tvContent.setText(pageText);
        sentences = SentenceSplitter.splitToSentences(pageText);
        pageInfo.setText(  (currentPage + 1) + "  /  " + pages.size() );
        pageSeekBar.setProgress(currentPage);
        currentSentenceIndex = 0;
    }

    private void nextPage() {
        if (currentPage < pages.size() - 1) {
            applyPageAnimation(true);
            updatePage(++currentPage);
        }
    }

    private void previousPage() {
        if (currentPage > 0) {
            applyPageAnimation(false);
            updatePage(--currentPage);
        }
    }

    private void applyPageAnimation(boolean forward) {
        int anim = forward ? R.anim.slide_in_right : R.anim.slide_in_left;
        tvContent.startAnimation(AnimationUtils.loadAnimation(this, anim));
    }

    private void startReadingPage() {
        if (sentences == null || sentences.isEmpty()) return;
        readSentence(currentSentenceIndex);
    }

    private void readSentence(int index) {
        if (index >= sentences.size()) {
            nextPage();
            startReadingPage();
            return;
        }
        currentSentenceIndex = index;
        String sentence = sentences.get(index);
        highlightSentence(sentence);
        ttsManager.speak(sentence);
    }

    private void highlightSentence(String sentence) {
        String fullText = tvContent.getText().toString();
        int start = fullText.indexOf(sentence);
        if (start >= 0) {
            int end = start + sentence.length();
            SpannableStringBuilder builder = new SpannableStringBuilder(fullText);
            builder.setSpan(new BackgroundColorSpan(0xFFCCE5FF), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvContent.setText(builder);

            final int scrollY = tvContent.getLayout().getLineTop(tvContent.getLayout().getLineForOffset(start));
            scrollView.post(() -> scrollView.smoothScrollTo(0, scrollY));
        }
    }

    private void onTtsDone() {
        runOnUiThread(() -> {
            if (isReading) {
                readSentence(currentSentenceIndex + 1);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ttsManager.shutdown();
    }
}
