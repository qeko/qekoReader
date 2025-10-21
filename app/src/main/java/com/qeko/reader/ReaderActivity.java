package com.qeko.reader;

import static android.content.ContentValues.TAG;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.Layout;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderActivity extends AppCompatActivity {
    private static final Logger log = LoggerFactory.getLogger(ReaderActivity.class);
    public AppPreferences appPreferences;
    public TextView textView;
    private Button btnTTS;
    private SeekBar seekBar;
    private TextView pageInfo;
    public TextToSpeechManager ttsManager;
    private ControlActivity controlActivity;
    //    public  boolean changeFontSize = false;
    private boolean isSimplified = true;

    public List<Integer> pageOffsets = new ArrayList<>();
    private List<Integer> pageOffsetsTemp = new ArrayList<>();
    public int currentPage = 0, totalPages = 0;
    private String[] currentSentences;
    private int sentenceIndex = 0;
    private boolean isSpeaking = false;

    private float speechRate;
    private float fontSize;

    private boolean isInitialLoad = true;
    public String filePath;
    private Dialog loadingDialog;
    private static final String FONT_PATH = "fonts/SimsunExtG.ttf";
//    private int lastPage;         ////////////////////////////////////////////////////////
//    private int lastSentence;  ////////////////////////////////////////////////////////
    private float lineSpacingMultiplier = 1.5f; // 示例值，也可以存储为用户偏好
    private boolean runPageOffsets = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        appPreferences = new AppPreferences(this);
        textView = findViewById(R.id.textContent);
        btnTTS = findViewById(R.id.btnTTS);
        seekBar = findViewById(R.id.pageSeekBar);
        pageInfo = findViewById(R.id.pageInfo);

        speechRate = appPreferences.getSpeechRate();
        fontSize = appPreferences.getFontSize();

        totalPages = appPreferences.getTotalPages();

        controlActivity = new ControlActivity(findViewById(R.id.controlPanel), this);
        textView.setTextSize(fontSize);
//
        textView.setLineSpacing(1.9f, lineSpacingMultiplier);

        filePath = getIntent().getStringExtra("filePath");
        currentPage = appPreferences.getCurrentPage(filePath);
        restoreUserSettings();
        loadText(filePath);
        InitialLoad();
        setupSeekBar();
        setupTouchControl();
        btnTTS.setOnClickListener(v -> toggleSpeaking());
    }


    private void InitialLoad(){
        // 优先加载缓存
        Log.d(TAG, "InitialLoad: "+filePath);
        pageOffsetsTemp = FileUtils.loadPageOffsets(this, filePath+"temp");
        pageOffsets = FileUtils.loadPageOffsets(this, filePath);

        if (pageOffsetsTemp == null) {
            pageOffsetsTemp = new ArrayList<>();
        }
        if (pageOffsets == null) {
            pageOffsets = new ArrayList<>();
        }

        if (!pageOffsets.isEmpty() && pageOffsets.size() > 0) {
            runPageOffsets = true;
            Log.d(TAG, "onCreate: 有缓存 "+ pageOffsets.size());
            //        pageOffsets = buildPageOffsets(filePath);   //测试时用
            totalPages = Math.max(1, pageOffsets.size() - 1);
            //              dismissLoadingDialog();
            Log.d(TAG, "InitialLoad: loadPage 1");
            loadPage(pageOffsets,currentPage);
        } else {
            runPageOffsets = false;
            Log.d(TAG, "onCreate: 无缓存 ");
            pageOffsetsTemp.clear();      //清空pageOffsetsTemp
//               pageOffsetsTemp.add(0);  //待确认
            // 无缓存 -> 重新分页
            Log.w(TAG, pageOffsetsTemp.isEmpty()+" buildPageOffsetsWithCache "+ pageOffsetsTemp.size());
            if (pageOffsetsTemp.isEmpty() && pageOffsetsTemp.size()== 0) {  //双无
                Log.d(TAG, "pageOffsetsTemp无  ");
                textView.post(() -> {
                    new Thread(() -> {
                        Log.w(TAG, "buildPageOffsetsWithCache true");
                        pageOffsetsTemp = buildPageOffsetsWithCache(filePath, true);
                        runOnUiThread(() -> {
                            Log.d(TAG, "InitialLoad: loadPage 2");
                            loadPage(pageOffsetsTemp, 0); // 立即显示临时分页第一页
                        });
                    }).start();
                });
            }
            totalPages = Math.max(1, pageOffsets.size() - 1);
//                    dismissLoadingDialog();
//                    loadPage(currentPage);
//        loadPage(pageOffsetsTemp != null && !pageOffsetsTemp.isEmpty() ? pageOffsetsTemp : pageOffsets, currentPage);
            Log.d(TAG, "InitialLoad: loadPage 3");
            runPageOffsets = true;
            loadPage( pageOffsets, currentPage);
        }

    }

    private int pageCharCount = 2000; // 默认值
    private int textLength = 0;

    public List<Integer> buildPageOffsetsWithCache(String filePath,boolean isNewPageOffers) {
        Log.w(TAG, " buildPageOffsetsWithCache isNewPageOffers "+filePath);
        Log.w(TAG, " buildPageOffsetsWithCache isNewPageOffers "+isNewPageOffers);

        List<Integer> thisPageOffsets = new ArrayList<>();
        // 先读取缓存
        textLength = appPreferences.getTextLength(filePath);
        pageCharCount = appPreferences.getPageCharCount(filePath);
       currentPage   = appPreferences.getCurrentPage(filePath);
        sentenceIndex = appPreferences.getSentenceIndex(filePath);

        // 如果缓存不存在，则重新估算 pageCharCount 和 textLength
        Log.w(TAG, "  textLength "+textLength);
        Log.w(TAG, "  pageCharCount "+pageCharCount);
        int viewWidth = 0;
        int viewHeight = 0;
        if (textLength <= 0 || pageCharCount <= 0) {
            Log.w(TAG, "TextLength/PageCharCount 无缓存，重新计算");

            viewWidth = textView.getWidth() - textView.getPaddingLeft() - textView.getPaddingRight();
            viewHeight = textView.getHeight() - textView.getPaddingTop() - textView.getPaddingBottom();

            if (viewWidth <= 0 || viewHeight <= 0) {
                Log.w(TAG, "TextView 宽高无效，无法估算分页字符数");
                return null;
            }
            // 先读取总长度
//            textLength = safeGetTextLength(filePath);
            textLength = getRealTextLength(filePath);
            appPreferences.saveTextLength(filePath,textLength);

            // 模拟一段文本来估算每页字符数
            TextPaint textPaint = textView.getPaint();
            String sampleText = "这是用于测量的示例文字。";
            StaticLayout layout = StaticLayout.Builder.obtain(sampleText, 0, sampleText.length(), textPaint, viewWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build();

            int lineHeight = layout.getLineBottom(0) - layout.getLineTop(0);
            int linesPerPage = viewHeight / lineHeight;
            int charsPerLine = 20; // 简单估算
            pageCharCount = Math.max(100, linesPerPage * charsPerLine);
            appPreferences.savePageCharCount(filePath,pageCharCount);

            Log.d(TAG, "重新计算分页: textLength=" + textLength + ", pageCharCount=" + pageCharCount);
        }

        // 开始分页
        thisPageOffsets.clear();
        thisPageOffsets.add(0);
        TextPaint paint = textView.getPaint();
        viewWidth = textView.getWidth() - textView.getPaddingLeft() - textView.getPaddingRight();
        viewHeight = textView.getHeight() - textView.getPaddingTop() - textView.getPaddingBottom() - 720;

        int start = 0;
        while (start < textLength) {
            int low = start + 1;
            int high = Math.min(textLength, start + pageCharCount * 2); // 上限两页
            int fitPos = start + 1;

            while (low <= high) {
                int mid = (low + high) / 2;
                String sub = readTextSegment(filePath, start, mid);
                StaticLayout layout = StaticLayout.Builder.obtain(sub, 0, sub.length(), paint, viewWidth)
                        .setLineSpacing(0f, 1.2f).setIncludePad(false).build();
                if (layout.getHeight() <= viewHeight) {
                    fitPos = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }

            if (fitPos <= start) break;
            thisPageOffsets.add(fitPos);

//            Log.w(TAG, "buildPageOffsetsWithCache"+ thisPageOffsets.size());
            // 克隆临时分页(边生成边显示)
            if (isNewPageOffers && thisPageOffsets.size() >= 30 ) {
                Log.d(TAG, "pageOffsetsTemp > 30");

                totalPages = thisPageOffsets.size() - 1;
                seekBar.setMax(Math.max(totalPages, 1));
                FileUtils.savePageOffsets(this, filePath+"temp", thisPageOffsets);
                return  thisPageOffsets;
            }
            start = fitPos;
        }

        totalPages = thisPageOffsets.size() - 1;
        seekBar.setMax(Math.max(totalPages, 1));
        FileUtils.savePageOffsets(this, filePath, thisPageOffsets);
        Log.d(TAG,  "PageOffsets="+thisPageOffsets.size());
        return thisPageOffsets;
    }


    private void restoreUserSettings() {
        speechRate  = appPreferences.getSpeechRate();
        Log.d(TAG,  "speechRate="+speechRate);

        fontSize = appPreferences.getFontSize();
        textView.setTextSize(fontSize);

        updateTheme(appPreferences.isDarkTheme());
        setFont(appPreferences.getFontName());

        // ✅ 只恢复 currentPage 和 sentenceIndex
        currentPage   = appPreferences.getCurrentPage(filePath);
        sentenceIndex = appPreferences.getSentenceIndex(filePath);

/*        lastPage = appPreferences.getLastPage();
        lastSentence = appPreferences.getLastSentence();

        currentPage = lastPage;
        sentenceIndex = lastSentence;*/
    }

    private boolean taskCompleted = false; // 任务完成状态

    private void loadText(String path) {
        try {
            File file = new File(path);
            String textFilePath;

            if (path.toLowerCase().endsWith(".pdf")) {
                textFilePath = path + ".pdftxt";
                if (!new File(textFilePath).exists()) {
                    textView.setText("首次打开要一些时间，请耐心等待或待会再来，如果看到乱码请退出再试一次");
                    FileUtils.extractTextFromPdf(file, this, "fonts/SimsunExtG.ttf");
                    return;
                }
            } else if (path.toLowerCase().endsWith(".epub")) {
                textFilePath = path + ".epubtxt";
                if (!new File(textFilePath).exists()) {

                    textView.setText("首次打开要一些时间，请耐心等待或待会再来，如果看到乱码请退出再试一次");
                    FileUtils.extractTextFromEpubByBatch(this, file, new File(textFilePath));
                    return;
                }
            } else {
                textFilePath = path;
            }

            // ✅ 统一走文本读取逻辑
            filePath = textFilePath;
//            fullText = readFileToString(new File(filePath));
            // 重新计算 textLength / pageCharCount
//            textLength = fullText.length();
//            textLength = safeGetTextLength(filePath);
            textLength = getRealTextLength(filePath);
            pageCharCount = appPreferences.getPageCharCount(filePath);

            // ✅ 恢复进度
            currentPage   = appPreferences.getCurrentPage(filePath);
            sentenceIndex = appPreferences.getSentenceIndex(filePath);
/*            currentPage = lastPage;
            sentenceIndex = lastSentence;*/

        } catch (Exception e) {
            Log.d(TAG, "loadText: 读取失败");
//            Toast.makeText(this, "读取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//            fullText = "";
        }
    }


    private int getRealTextLength(String  filePath) {
        File file = new File(filePath);
        Charset charset = detectEncoding(file);
        int length = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset))) {
            while (reader.read() != -1) {
                length++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return length;
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

    public void loadPage(List<Integer> pageOffsets,int page) {
        Log.d("loadPage", totalPages + " loadPage1- " + page);
        Log.d("loadPage", totalPages + " pageOffsets- " + pageOffsets);

        Log.d("loadPage", totalPages + "page >= pageOffsets.size()- " +( page >= pageOffsets.size()));
        if (pageOffsets == null || page < 0 || page >= pageOffsets.size()) return;
        Log.d("loadPage", totalPages + " loadPage2- " + page);
        if (page < 0 || page >= totalPages) return;
        Log.d("loadPage", totalPages + " loadPage3- " + page);

        // 这里不要再用 fullText.length()，改用持久化的 textLength
        textLength = appPreferences.getTextLength(filePath);
        Log.d("loadPage", totalPages + " loadPage4- " + page);
        Log.d("TAG", "textLength = " + textLength);

        int start = pageOffsets.get(page);
        int end = (page + 1 < pageOffsets.size()) ? pageOffsets.get(page + 1) : textLength;

        Log.d("TAG", start + " loadPage:pageText " + end);

        // 防御：确保 start 和 end 合法
        if (start < 0) start = 0;
        if (end > textLength) end = textLength;
        if (end < start) end = start;

        // 每次只加载一段内容，而不是全文
        Log.d(TAG, "readTextSegment: "+filePath);
        String pageText = readTextSegment(filePath, start, end);

        currentSentences = pageText.split("(?<=[.,，?!。！？])");

        if (isInitialLoad && page == currentPage) {
//            int lastSentence = PreferenceManager.getDefaultSharedPreferences(this).getInt("lastSentence", 0);  //改
//            int lastSentence = this.appPreferences.getLastSentence();
            sentenceIndex = Math.min(currentPage, currentSentences.length - 1);
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
                if (ttsManager == null) {
                    ttsManager = new TextToSpeechManager(this, this::onTtsDone);
                    ttsManager.setSpeed(speechRate);
                    // 自动点击
                    new Handler().postDelayed(() -> {
                        // 模拟点击事件
                        if (0 == TextToSpeech.SUCCESS) {
                            toggleSpeaking();
                        }
                    }, 2000);
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
        if (currentSentences == null) return;

        if (sentenceIndex >= currentSentences.length) {
            if (currentPage < totalPages - 1) {
                // 下一页
                currentPage++;
                sentenceIndex = 0;
                appPreferences.saveCurrentPage(filePath, currentPage);

                loadPage(pageOffsetsTemp != null && !pageOffsetsTemp.isEmpty()
                        ? pageOffsetsTemp : pageOffsets, currentPage);

                // 等 loadPage 完成后，再继续朗读
                speakNextSentence();
            } else {
                // 已到最后一页
                isSpeaking = false;
                btnTTS.setText("▶️");
                highlightSentence(-1);
            }
            return;
        }

        String sentence = currentSentences[sentenceIndex];
        highlightSentence(sentenceIndex);

        // 清理句子
        sentence = sentence.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9\\s]{3,}", "")
                .replaceAll("[\"“”]", "")
                .replaceAll("\\.", "");

//        Log.d(TAG, "speak:"+sentence);
        ttsManager.speak(sentence);
    }

    private void onTtsDone() {
        int globalOffset = 0;
        if (pageOffsets != null && !pageOffsets.isEmpty()) {
            if (currentPage >= pageOffsets.size()) {
                currentPage = pageOffsets.size() - 1;
                sentenceIndex = 0;
            }
            globalOffset = pageOffsets.get(currentPage);
        }

        // 粗略：句子前几个字的 offset
        if (currentSentences != null && sentenceIndex < currentSentences.length) {
            globalOffset += currentSentences[sentenceIndex].length();
        }

        appPreferences.saveProgress(filePath, currentPage, sentenceIndex, globalOffset);
        appPreferences.setLastPage(currentPage);
        appPreferences.setLastSentence(sentenceIndex);

        appPreferences.saveCurrentPage(filePath, currentPage);
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
//                    loadPage(p);
                    Log.d(TAG, "setupSeekBar: loadPage 5");
//                    loadPage( pageOffsets, p);
                    loadPage(pageOffsetsTemp != null && !pageOffsetsTemp.isEmpty() ? pageOffsetsTemp : pageOffsets, p);
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
//                        loadPage(currentPage);
                        Log.d(TAG, "setupTouchControl: loadPage 6");
//                        loadPage(pageOffsets, currentPage);
                        loadPage(pageOffsetsTemp != null && !pageOffsetsTemp.isEmpty() ? pageOffsetsTemp : pageOffsets, currentPage);
                    }
                } else if (x > width * 2 / 3f) {
                    if (currentPage < totalPages - 1) {
                        currentPage++;
//                        loadPage(currentPage);
                        Log.d(TAG, "setupTouchControl: loadPage 7");
//                        loadPage(pageOffsets, currentPage);
                        loadPage(pageOffsetsTemp != null && !pageOffsetsTemp.isEmpty() ? pageOffsetsTemp : pageOffsets, currentPage);
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
/*        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString("lastFilePath", getIntent().getStringExtra("filePath"))
                .putInt("lastPage", currentPage)
                .putInt("lastSentence", sentenceIndex)
                .apply();*/

        appPreferences.saveCurrentPage(filePath,currentPage);
        appPreferences.setTotalPages(totalPages);

        appPreferences.setSpeechRate(speechRate);
        appPreferences.setFontSize(fontSize);
        appPreferences.setLastPage(currentPage);
        appPreferences.setLastSentence(sentenceIndex);



        appPreferences.setLastFilePath(filePath);
        appPreferences.setMaxCharsPerPage(appPreferences.getMaxCharsPerPage());

        if (ttsManager != null) {
            ttsManager.stop();
            ttsManager.shutdown();
        }
        super.onDestroy();
    }


    public void adjustFontSize(float delta) {
        float newSize = textView.getTextSize() / getResources().getDisplayMetrics().scaledDensity + delta;
        textView.setTextSize(newSize);
        this.appPreferences.setFontSize(newSize);
//        PreferenceManager.getDefaultSharedPreferences(this).edit()
//                .putFloat("fontSize", newSize).apply();

/*        textView.postDelayed(() -> {
            buildPageOffsets();
            loadPage(currentPage);
        }, 200);*/
    }

    public void updateTheme(boolean isDark) {
//        PreferenceManager.getDefaultSharedPreferences(this).edit()
//                .putBoolean("isDark", isDark).apply();
        this.appPreferences.setDarkTheme(isDark);

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

    private String readTextSegment(String filePath, int start, int end) {
        File file = new File(filePath);
        Charset charset = detectEncoding(file);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset))) {
            reader.skip(start);
            char[] buf = new char[end - start];
            int read = reader.read(buf, 0, end - start);
            if (read > 0) {
                return new String(buf, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
}
