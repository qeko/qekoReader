package com.qeko.reader;


import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qeko.tts.TextToSpeechManager;
import com.qeko.utils.AppPreferences;
import com.qeko.utils.FileUtils;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ReaderActivity extends AppCompatActivity {

    private static final String TAG = "ReaderActivity";
    private String filePath;
//    private static final String CACHE_FILE = "page_offsets.dat";

    private TextView textView;
    private TextView pageInfo;
    private Button btnTTS;
    private SeekBar pageSeekBar;
    public TextToSpeechManager ttsManager;

    private File file;
    private Charset charset;



    private ReaderSettingsManager settingsManager;
    public AppPreferences appPreferences;

    private PageSplitter splitter;

    private List<Long> pageOffsetList = new ArrayList<>();
    private int currentPage = 1;        // 1-based page index for UI
    private long currentStartByte = 0;  // 当前页面起始字节
    private String[] currentSentences;
    private int currentSentenceIndex = 0;
    private LinearLayout settingsPanel;
    private Handler mainHandler;
//    private Window window = getWindow();
    private volatile boolean isPaging = false;
    private boolean initTTS = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        ttsManager = new TextToSpeechManager(this, this::onTtsDone);


        textView = findViewById(R.id.textView);
        pageSeekBar = findViewById(R.id.pageSeekBar);
        pageInfo = findViewById(R.id.pageInfo);
        btnTTS = findViewById(R.id.btnTTS);
        settingsPanel = findViewById(R.id.settingsPanel);
        filePath = getIntent().getStringExtra("filePath");
        file = new File(filePath);
                //是否可以在这抽取

        appPreferences = new AppPreferences(this);
        ttsManager.setSpeed(appPreferences.getSpeechRate());

        settingsManager = new ReaderSettingsManager(this);
        settingsManager.initViews();


        mainHandler = new Handler(Looper.getMainLooper());

        restoreReaderSettings();

        findViewById(R.id.btnToggleInvert).setOnClickListener(v -> {
            toggleInvertMode();
        });

        findViewById(R.id.btnApplySettings).setOnClickListener(v -> {
            // 1. 保存所有设置（已实时保存，这里只补充必要处理）
            float brightness = appPreferences.getBrightness();
            settingsManager.changeBrightness(brightness);
            // 3. 隐藏设置面板
            settingsPanel.setVisibility(View.GONE);

            // 如果变更了字体、行距 → 执行重新分页
            if (settingsManager.getChange()) {
                  settingsManager.setChange(false);
//                rebuildPaginationAndRestore();  // ←———— 核心
//                Log.d(TAG, "重新分页");
                pageOffsetList.clear();//触发重新分页
                  this.startPaginationIfNeeded();
            }

        });
//        speakNextSentence();
//        toggleSpeaking();

        openBook(file);           // 打开书本
        setupSettingButtons();    // 初始化按钮事件
        // 2️⃣ 延迟3秒后执行打开书和设置按钮
/*        mainHandler.postDelayed(() -> {
            openBook(file);           // 打开书本
            setupSettingButtons();    // 初始化按钮事件
        }, 2000);*/


    }

    private void setupSettingButtons() {
        findViewById(R.id.btnIncreaseFontSize).setOnClickListener(v -> {
            settingsManager.changeFontSize(+1f);

            float sp = textView.getTextSize() / getResources().getDisplayMetrics().scaledDensity + 1f;
            if (sp < 8f) sp = 8f;
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            appPreferences.saveTextSizeSp(sp);

        });
        findViewById(R.id.btnDecreaseFontSize).setOnClickListener(v -> {
            settingsManager.changeFontSize(-1f);
            float sp = textView.getTextSize() / getResources().getDisplayMetrics().scaledDensity - 1f;
            if (sp < 8f) sp = 8f;
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            appPreferences.saveTextSizeSp(sp);
        });

// 行距 + 按钮
        findViewById(R.id.btnIncreaseLineSpacing).setOnClickListener(v -> {
            settingsManager.changeLineSpacing(+0.1f);

            float spacing = textView.getLineSpacingMultiplier() + 0.1f;
            if (spacing < 1.0f) spacing = 1.0f;

            textView.setLineSpacing(0f, spacing);
            appPreferences.saveLineSpacing(spacing);
        });
// 行距 - 按钮
        findViewById(R.id.btnDecreaseLineSpacing).setOnClickListener(v -> {
            settingsManager.changeLineSpacing(-0.1f);

            float spacing = textView.getLineSpacingMultiplier() - 0.1f;
            if (spacing < 1.0f) spacing = 1.0f;

            textView.setLineSpacing(0f, spacing);
            appPreferences.saveLineSpacing(spacing);

        });


// 亮度 +
        findViewById(R.id.btnIncreaseBrightness).setOnClickListener(v -> {

            changeBrightness(+0.05f);

        });

// 亮度 -
        findViewById(R.id.btnDecreaseBrightness).setOnClickListener(v -> {
            changeBrightness(-0.05f);

        });


        // ========================
//       语速 + 按钮
// ========================
        findViewById(R.id.btnIncreaseSpeed).setOnClickListener(v -> {

            float oldRate = appPreferences.getSpeechRate();
            float newRate = oldRate + 0.1f;

            if (newRate > 2.0f) newRate = 2.0f;

            // 更新设置管理器
            settingsManager.changeSpeechRate(newRate - oldRate);

            // 应用到 TTS
            ttsManager.setSpeed(newRate);

            // 保存
            appPreferences.setSpeechRate(newRate);

            Log.d("TTS", "Speed increased to: " + newRate);
        });


// ========================
//       语速 - 按钮
// ========================
        findViewById(R.id.btnDecreaseSpeed).setOnClickListener(v -> {

            float oldRate = appPreferences.getSpeechRate();
            float newRate = oldRate - 0.1f;

            if (newRate < 0.5f) newRate = 0.5f;

            // 更新设置管理器
            settingsManager.changeSpeechRate(newRate - oldRate);

            // 应用到 TTS
            ttsManager.setSpeed(newRate);

            // 保存
            appPreferences.setSpeechRate(newRate);

            Log.d("TTS", "Speed decreased to: " + newRate);
        });
    }

    public void toggleInvertMode() {
        boolean enabled = !appPreferences.isInvertMode();
        appPreferences.saveInvertMode(enabled);

        if (enabled) {
            textView.setBackgroundColor(Color.BLACK);
            textView.setTextColor(Color.WHITE);
        } else {
            textView.setBackgroundColor(Color.WHITE);
            textView.setTextColor(Color.BLACK);
        }
    }

//    public void applyBrightness(float value) {
//        // 保存
//        appPreferences.saveBrightness(value);
//
//        WindowManager.LayoutParams lp = window.getAttributes();
//        lp.screenBrightness = value;   // 0.0f~1.0f
//        window.setAttributes(lp);
//    }


    private void changeBrightness(float delta) {
        Window window = getWindow(); // Activity 的 window
        WindowManager.LayoutParams lp = window.getAttributes();
        float brightness = lp.screenBrightness;
        if (brightness < 0f) brightness = 0.5f; // 默认
        brightness += delta;
        if (brightness < 0.01f) brightness = 0.01f;
        if (brightness > 1f) brightness = 1f;
        lp.screenBrightness = brightness;
        window.setAttributes(lp);
        // 保存到偏好
        appPreferences.saveBrightness(brightness);
    }

/*
    private void updateTtsSpeed(float speed) {
        if (ttsManager != null) {
            ttsManager.setSpeed(speed);
        }
    }*/

    // ========== 抽取完成后初始化分页和显示 ==========
    private void initAfterTextExtraction(File textFile) {
        this.file = textFile;

        // 检测编码
        charset = detectEncoding(file);
        if (charset == null) charset = StandardCharsets.UTF_8;

        // 尝试加载缓存偏移表
        pageOffsetList = loadPageOffsets();

        // （如果缓存为空或者需要重新分页）
        startPaginationIfNeeded();

        // 显示第一页
        if (pageOffsetList.size() >= 1) {
            showPage(0);
        }

        setupSeekBar();
        setupTouchControl();

        btnTTS.setOnClickListener(v -> toggleSpeaking());

        restoreProgressIfAny();
    }

    public void openBook(File originalFile) {
        if (originalFile == null || !originalFile.exists()) return;

        String path = originalFile.getAbsolutePath();
        File textFile;

        if (path.toLowerCase().endsWith(".pdf")) {
            textFile = new File(path + ".pdftxt");

            // 如果文本还没生成，可触发后台抽取（增量安全）
            if (!textFile.exists()) {
                textView.setText("首次打开要一些时间，请耐心等待...");
                FileUtils.extractTextFromPdfIncrementalSafe(originalFile, this, appPreferences, path);
                return; // 等待后台抽取完成后再打开
            }

        } else if (path.toLowerCase().endsWith(".epub")) {
            textFile = new File(path + ".epubtxt");

            // 如果文本还没生成，可触发后台抽取（增量安全）
            if (!textFile.exists()) {
                textView.setText("首次打开要一些时间，请耐心等待...");
                FileUtils.extractEpubIncrementalSafe(originalFile, textFile, this, appPreferences, path);
                return; // 等待后台抽取完成后再打开
            }

        } else {
            // 其他文本文件直接使用
            textFile = originalFile;
        }

        // 文本文件已存在，初始化 ReaderActivity
        initAfterTextExtraction(textFile);
    }


    // ========== 分页启动（后台） ==========
    private void startPaginationIfNeeded() {
        if (isPaging) return;

//        boolean needPaging = (pageOffsetList.size() <= 1) || controlActivity.isForceRebuildPages();
        boolean needPaging = (pageOffsetList.size() <= 1);

        if (!needPaging) return;
//        Log.d(TAG, "重新分页，调用startPaginationIfNeeded");

        isPaging = true;
//        Toast.makeText(this, "请稍候...", Toast.LENGTH_SHORT).show();

        textView.post(() -> {
            splitter = new PageSplitter(file, textView);
            updatePagingParams(); // 🔥 同步最新字体/行距/宽高

            new Thread(() -> {
                try {
                    splitter.buildPageOffsets(settingsManager.getLineSpacing());
                    List<Long> newList = splitter.pageOffsetList;

                    mainHandler.post(() -> {
                        if (newList != null && newList.size() > 1) {
                            pageOffsetList = new ArrayList<>(newList);
                            savePageOffsets(pageOffsetList);

                            int newPageIndex = findPageByOffset(pageOffsetList, currentStartByte);
                            showPage(newPageIndex);
                        }
                        isPaging = false;
                    });
                } catch (Exception e) {
                    Log.e(TAG, "分页异常", e);
                    mainHandler.post(() -> {
                        Toast.makeText(this, "分页失败", Toast.LENGTH_SHORT).show();
                        isPaging = false;
                    });
                }
            }).start();
        });
    }


/*

    private void extractRemainingPagesInBackground(File pdfFile, File outFile, int startPage) {
        FileUtils.extractTextFromPdfIncremental(pdfFile, outFile, this, "fonts/SimsunExtG.ttf", startPage, Integer.MAX_VALUE,
                new FileUtils.ExtractProgressCallback() {
                    @Override
                    public void onProgress(int progress) {
                        // 后台抽取进度不用显示，直接打印日志可选
                        Log.d(TAG, "处理中: " + progress + "%");
                    }

                    @Override
                    public void onDone() {
                        Log.d(TAG, "全部处理取完成");
                        // 可选择重新分页或刷新分页缓存
                        rebuildPaginationAndRestore();
                    }
                });
    }
*/


    // ========== 加载并显示页（0-based 页索引） ==========
    private void showPage(int pageIndex0) {
        if (pageOffsetList == null || pageOffsetList.size() == 0) return;

        // pageIndex0 范围：0 .. size()-2 （因为最后一项为文件尾）
        if (pageIndex0 < 0) pageIndex0 = 1;
        if (pageIndex0 >= pageOffsetList.size() - 1) pageIndex0 = pageOffsetList.size() - 2;
//        Log.d(TAG, "showPage: pageIndex0 "+pageIndex0);
//        Log.d(TAG, "showPage: pageOffsetList.size "+pageOffsetList.size());
        long start = pageOffsetList.get(pageIndex0);
        long end = pageOffsetList.size() > pageIndex0 + 1 ? pageOffsetList.get(pageIndex0 + 1) : file.length();

        // 读取并显示
        String text = loadTextFromTo(start, end);
        displayPageTextAndPrepareTTS(text);

        // 更新状态
        currentPage = pageIndex0 + 1; // UI 上用 1-based
        currentStartByte = start;

        // update UI
        pageSeekBar.setMax(Math.max(1, pageOffsetList.size() - 1));
        pageSeekBar.setProgress(currentPage);
        pageInfo.setText(currentPage + " / " + Math.max(1, pageOffsetList.size() - 1));
    }

    // 根据字节范围读取文本（安全）
    private String loadTextFromTo(long start, long end) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (start < 0) start = 0;
            if (end > raf.length()) end = raf.length();
            int len = (int) (end - start);
            if (len <= 0) return "";
            byte[] buf = new byte[len];
            raf.seek(start);
            raf.readFully(buf);
            return new String(buf, charset);
        } catch (Exception e) {
            Log.e(TAG, "loadTextFromTo error", e);
            return "";
        }
    }

    // 将文本放入 textView 并准备分句/TTS
    private void displayPageTextAndPrepareTTS(String text) {
        textView.setText(text);
        textView.scrollTo(0, 0);

        currentSentenceIndex = 0; // 每次新页从0开始，可在恢复进度时重设

        textView.post(() -> {
            Layout layout = textView.getLayout();
            if (layout == null) return;

            int lastVisibleLine = layout.getLineCount() - 1;
            if (lastVisibleLine < 0) lastVisibleLine = 0;
            int visibleEnd = layout.getLineEnd(lastVisibleLine);
            String visibleText = text.substring(0, Math.min(visibleEnd, text.length()));

            // 公用正则
            currentSentences = splitSentences(visibleText);

            if (currentSentenceIndex < 0) currentSentenceIndex = 0;
            if (currentSentenceIndex >= currentSentences.length) currentSentenceIndex = 0;

            // 🔥 分句完成后立即开始朗读
//            Log.d(TAG, "displayPageTextAndPrepareTTS: ");

            speakNextSentence();
        });
    }


    // 翻页触控（保留）
    private void setupTouchControl() {
        textView.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                float x = e.getX();
                float width = textView.getWidth();
                if (x > width * 2 / 3f) nextPage();
                else if (x < width / 3f) prevPage();
                else toggleSettingsPanel();
            }
            return true;
        });
    }

    // loadPage API（1-based page）
    private void loadPage(int page) {
        if (page <= 0) return;

        // 当 page 大于已生成的最大页时，若正在分页则提示；否则限制到最后页
        int generatedPages = Math.max(0, pageOffsetList.size() - 1);
        if (page > generatedPages) {
            if (isPaging) {
//                Toast.makeText(this, "正在分页，暂不可跳转到该页", Toast.LENGTH_SHORT).show();
                return;
            } else {
                page = generatedPages;
            }
        }


        int pageIndex0 = page - 1;
        showPage(pageIndex0);
    }

    private void nextPage() {
        if (currentPage < Math.max(1, pageOffsetList.size() - 1)) {
            loadPage(currentPage + 1);
        } else {
            if (isPaging){}
//                Toast.makeText(this, "正在分页，已显示最后已生成页", Toast.LENGTH_SHORT).show();
            else Toast.makeText(this, "已是最后一页", Toast.LENGTH_SHORT).show();
        }
    }

    private void prevPage() {
        if (currentPage > 1) loadPage(currentPage - 1);
    }

    // ========== SeekBar =============
    private void setupSeekBar() {
        pageSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    loadPage(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    // ========== TTS 逐句相关 ==========
    private void speakNextSentence() {
        if (currentSentences == null || currentSentenceIndex >= currentSentences.length) return;
        highlightSentence(currentSentences[currentSentenceIndex]);
//        Log.d(TAG, "speakNextSentence: "+currentSentences[currentSentenceIndex]);

        if(initTTS) {
            initTTS = false;
            mainHandler.postDelayed(() -> {
                ttsManager.speak(currentSentences[currentSentenceIndex]);
            }, 2000);
        }
        ttsManager.speak(currentSentences[currentSentenceIndex]);

    }

    private void onTtsDone() {

        if (currentSentences == null || currentSentences.length == 0) {
            return;
        }

        // 当前页还有下一句 → 继续读下一句
        if (currentSentenceIndex < currentSentences.length - 1) {

            currentSentenceIndex++;

            // 保存进度
            appPreferences.saveProgress(
                    filePath,
                    currentStartByte,        // byte offset
                    currentSentenceIndex,    // sentence index
                    currentPage              // page index
            );
//            Log.d(TAG, "onTtsDone: ");
            speakNextSentence();
            return;
        }

        // 页内句子读完了
        // ==============================
        //       页内已读完 → 翻页
        // ==============================
        if (currentPage < pageOffsetList.size() - 1) {

            int nextPage = currentPage + 1;

            // 保存进度：下一页，从句0开始
            appPreferences.saveProgress(
                    filePath,
                    pageOffsetList.get(nextPage),
                    0,
                    nextPage
            );

/*            appPreferences.saveProgress(
                    filePath,
                    currentStartByte,        // byte offset
                    currentSentenceIndex,    // sentence index
                    currentPage              // page index
            );*/

            // loadPage() 内会显示页面 → displayPageTextAndPrepareTTS()
            // → 分句完成后会自动调用 speakNextSentence()
            loadPage(nextPage);

            return;
        }

        // ==============================
        //       已到最后一页
        // ==============================
        if (isPaging) {
            Toast.makeText(this, "分页中，稍后继续朗读", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "朗读完成", Toast.LENGTH_SHORT).show();
        }
    }

    private String[] splitSentences(String text) {
        if (text == null || text.isEmpty()) return new String[]{""};
        return text.split("(?<=[。． ，, ！!？?])");

    }


    private void highlightSentence(String sentence) {
        CharSequence current = textView.getText();
        if (current == null) return;
        SpannableString spannable = new SpannableString(current);
        int start = current.toString().indexOf(sentence);
        if (start >= 0) {
            int end = start + sentence.length();
            spannable.setSpan(new BackgroundColorSpan(0xFFFFFF00), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(spannable);
        }
    }

    private void toggleSpeaking() {
//        Log.d(TAG, "toggleSpeaking: 111");
        if(btnTTS.getText().equals("🔇"))
        {
//            Log.d(TAG, "toggleSpeaking: 222");
            btnTTS.setText("🎧");
            // 先确保 currentSentences 已准备
            if (currentSentences == null || currentSentences.length == 0) {
//                Log.d(TAG, "toggleSpeaking: 2525");

                // 重新准备当前页
                showPage(currentPage - 1);
            }
            // 读取 appPreferences 中保存的句子索引（如果打开时恢复）
//            Log.d(TAG, "toggleSpeaking: ");
            speakNextSentence();
        }else
        {
//            Log.d(TAG, "toggleSpeaking: 333");
            btnTTS.setText("🔇");
            ttsManager.stop();
        }
    }

    /*
        if (ttsManager.isSpeaking()) {
            ttsManager.stop();
            btnTTS.setText("🔇");
        } else {
//            controlActivity.hide();
            btnTTS.setText("🎧");
            // 先确保 currentSentences 已准备
            if (currentSentences == null || currentSentences.length == 0) {
                // 重新准备当前页
                showPage(currentPage - 1);
            }
            // 读取 appPreferences 中保存的句子索引（如果打开时恢复）
            speakNextSentence();
        }*/

    // ========== 保存/加载分页缓存 ==========
    private void savePageOffsets(List<Long> list) {
        try (ObjectOutputStream oos = new ObjectOutputStream(openFileOutput(getCacheFileName(), MODE_PRIVATE))) {
            oos.writeObject(list);
            Log.i(TAG, "已保存分页缓存，共 " + Math.max(0, list.size() - 1) + " 页");
        } catch (Exception e) {
            Log.e(TAG, "保存分页缓存失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> loadPageOffsets() {
        try (ObjectInputStream ois = new ObjectInputStream(openFileInput(getCacheFileName()))) {
            List<Long> list = (List<Long>) ois.readObject();
            // basic validation
            if (list != null && list.size() > 1) {
                Log.i(TAG, "加载到分页缓存，共 " + (list.size() - 1) + " 页");
                return list;
            }
        } catch (Exception e) {
            // ignore
        }
        return new ArrayList<>();
    }

    // ========== 保存/恢复阅读进度 ==========
    @Override
    protected void onPause() {
        super.onPause();
        // 保存进度：当前页起始字节 & 当前句索引 & 当前页
        appPreferences.saveProgress(
                filePath,
                currentStartByte,        // byte offset
                currentSentenceIndex,    // sentence index
                currentPage              // page index
        );
    }

    private void restoreProgressIfAny() {
        Log.d(TAG, "restoreProgressIfAny: "+filePath);
        long savedOffset = appPreferences.getSavedOffset(filePath);
        int savedSentenceIndex = appPreferences.getSavedSentenceIndex(filePath);
        int savedPage = appPreferences.getSavedPage(filePath);


        if (savedOffset > 0) {
            // 如果分页列表已经生成，直接定位；否则在分页完成后会自动 restore
            if (pageOffsetList != null && pageOffsetList.size() > 1) {
                int pageIdx = findPageByOffset(pageOffsetList, savedOffset);
                currentSentenceIndex = savedSentenceIndex;
                showPage(pageIdx);
            } else {
                // 分页未完成时：先保留 currentStartByte, sentenceIndex。 当分页完成后 startPagination 回调会定位
                currentStartByte = savedOffset;
                currentSentenceIndex = savedSentenceIndex;
                currentPage = Math.max(1, savedPage);
            }
        }


    }

    // 找到包含 offset 的 page 索引（0-based）
    private int findPageByOffset(List<Long> list, long offset) {
        if (list == null || list.size() <= 1) return 0;
        for (int i = 0; i < list.size() - 1; i++) {
            long s = list.get(i);
            long e = list.get(i + 1);
            if (offset >= s && offset < e) return i;
        }
        return list.size() - 2;
    }


/*
    // 保留当前位置（currentStartByte）, 重新分页后用 findPageByOffset 定位
    public void rebuildPaginationAndRestore() {   //合并到 startpagination
        if (isPaging) return;
        isPaging = true;

        long savedOffset = currentStartByte;
        int savedSentence = currentSentenceIndex;
        int savedPage = currentPage;

        Toast.makeText(this, "正在重新分页，请稍候...", Toast.LENGTH_SHORT).show();

        textView.post(() -> {
*/
/*            splitter = new PageSplitter(file, textView);

            // 🔥 设置最新字体和行距
            splitter.setTextSize(textView.getTextSize());
            splitter.setLineSpacingMultiplier(currentLineSpacing);
            splitter.setPageWidth(textView.getWidth()); //- textView.getPaddingLeft() - textView.getPaddingRight()
            splitter.setPageHeight(textView.getHeight() ) ;*//*

            updatePagingParams();
            Log.d(TAG, "行高: "+ (textView.getHeight() ));
            new Thread(() -> {
                try {
                    splitter.buildPageOffsets(1.0f);
                    List<Long> newList = splitter.pageOffsetList;

                    mainHandler.post(() -> {
                        if (newList != null && newList.size() > 1) {
                            pageOffsetList = new ArrayList<>(newList);
                            savePageOffsets(pageOffsetList);

                            // 定位旧 offset
                            int newPageIndex = findPageByOffset(pageOffsetList, savedOffset);
                            currentSentenceIndex = savedSentence; // 恢复句索引
                            showPage(newPageIndex);

                            Toast.makeText(this, "重新分页完成", Toast.LENGTH_SHORT).show();
                        }
                        isPaging = false;
                    });
                } catch (Exception e) {
                    Log.e(TAG, "重新分页失败", e);
                    mainHandler.post(() -> {
                        Toast.makeText(this, "重新分页失败", Toast.LENGTH_SHORT).show();
                        isPaging = false;
                    });
                }
            }).start();
        });
    }

*/

    private void restoreReaderSettings() {

        // ========== 亮度 ==========
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = appPreferences.getBrightness();
        getWindow().setAttributes(lp);

        // ========== 反白（夜间模式） ==========
        boolean invert = appPreferences.isInvertMode();
        if (invert) {
            textView.setBackgroundColor(Color.BLACK);
            textView.setTextColor(Color.WHITE);
        } else {
            textView.setBackgroundColor(Color.WHITE);
            textView.setTextColor(Color.BLACK);
        }

        // ========== 字体 ==========
        String fontPath = appPreferences.getFontPath();
        try {
            Typeface tf = Typeface.createFromAsset(getAssets(), fontPath);
            textView.setTypeface(tf);
        } catch (Exception e) {
            Log.e("Reader", "字体加载失败：" + fontPath);
        }

        // ========== 字号 ==========
        float savedSp = appPreferences.getTextSizeSp( 16f); // 若你用不同名请改
        Log.d(TAG, "global_text_size:= " + savedSp);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, savedSp);

        // ========== 行距 ==========
        float spacing = appPreferences.getLineSpacing();
        textView.setLineSpacing(0, spacing);

        // ★★★★★
        // 设置恢复后必须重新分页（保持当前页位置）
//        updatePagingParams();
//        rebuildPaginationAndRestore();
    }


    private void updatePagingParams() {
        if (textView == null || splitter == null) return;

        // 1. 字体大小
        float textSize = textView.getTextSize(); // px
        splitter.setTextSize(textSize);

        // 2. 行距倍数
        splitter.setLineSpacingMultiplier(currentLineSpacing);

        // 3. 可用宽度/高度
        int width = textView.getWidth() - textView.getPaddingLeft() - textView.getPaddingRight();
        int height = textView.getHeight()  - 900;
        splitter.setPageWidth(width);
        splitter.setPageHeight(height);

        Log.d(TAG, "updatePagingParams: size=" + textSize + "  lineSpace=" + currentLineSpacing +
                "  pageWidth=" + width + "  pageHeight=" + height);
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
                try {
                    return Charset.forName(encoding);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        // ----------- 强制安全 fallback -------------
        try {
            return Charset.forName("UTF-8");
        } catch (Exception e) {
            return StandardCharsets.UTF_8; // 永远不会失败
        }
    }

    private float currentLineSpacing = 1.5f; // 默认 1.5 倍行距

/*

    public void adjustFontSize(float deltaSp) {
        // 先读取当前显示的 px -> 转为 sp
        float px = textView.getTextSize(); // px
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        float currentSp = px / scaledDensity;

        // 应用增量（deltaSp 单位：sp）
        float newSp = currentSp + deltaSp;
        if (newSp < 8f) newSp = 8f;      // 限制最小字体
        if (newSp > 200f) newSp = 200f;  // 限制最大字体

        // 设置到 TextView（指定单位为 SP，避免歧义）
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSp);

        // 保存为 SP（用你新的方法）
*/
/*        if (file != null) {
            appPreferences.saveTextSizeSp(file.getAbsolutePath(), newSp);
        } else {
            // 若无文件上下文，可保存为全局默认 key*//*

        appPreferences.saveTextSizeSp("global_text_size", newSp);
//        }

        // 字号变了需要重新分页 / 更新参数
*/
/*        updatePagingParams();
        rebuildPaginationAndRestore();*//*

    }


    public void adjustLineSpace(float delta) {
        currentLineSpacing += delta;
        if (currentLineSpacing < 1f) currentLineSpacing = 1f;
        textView.setLineSpacing(0f, currentLineSpacing);
        appPreferences.saveLineSpacing(currentLineSpacing); // 保存当前行距
    }
*/


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ttsManager != null) ttsManager.shutdown();
    }


    // 显示/隐藏设置面板
    private void toggleSettingsPanel() {
        if (settingsPanel.getVisibility() == View.VISIBLE) settingsPanel.setVisibility(View.GONE);
        else settingsPanel.setVisibility(View.VISIBLE);
    }

    private String getCacheFileName() {
        String path = file.getAbsolutePath();
        long lastMod = file.lastModified();
        long size = file.length();

        // 构造唯一字符串
        String key = path + "_" + lastMod + "_" + size;

        // 转为安全文件名（仅由 0~9a~f 组成）
        String md5 = md5(key);

        return "page_offsets_" + md5 + ".dat";
    }

    private String md5(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }



}

