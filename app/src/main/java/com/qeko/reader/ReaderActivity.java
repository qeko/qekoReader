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
//import com.ibm.icu.text.Transliterator;

public class ReaderActivity extends AppCompatActivity {
    private static final Logger log = LoggerFactory.getLogger(ReaderActivity.class);
    private AppPreferences appPreferences;
    public TextView textView;
    private Button btnTTS;
    private SeekBar seekBar;
    private TextView pageInfo;
    public TextToSpeechManager ttsManager;
    private ControlActivity controlActivity;

    private boolean isSimplified = true;

//    private String fullText = "";
    private List<Integer> pageOffsets = new ArrayList<>();
    private List<Integer> pageOffsetsTemp = new ArrayList<>();
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
    private int lastPage;
    private int lastSentence;
    private float lineSpacingMultiplier = 1.5f; // 示例值，也可以存储为用户偏好
    private boolean runPageOffsets = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
//        showLoadingDialog();

        appPreferences = new AppPreferences(this);


        textView = findViewById(R.id.textContent);
        btnTTS = findViewById(R.id.btnTTS);
        seekBar = findViewById(R.id.pageSeekBar);
        pageInfo = findViewById(R.id.pageInfo);

        speechRate = appPreferences.getSpeechRate();
        fontSize = appPreferences.getFontSize();

        totalPages = appPreferences.getTotalPages();

//        if (null == ttsManager)   ttsManager = new TextToSpeechManager(this, speechRate, this::onTtsDone);
        controlActivity = new ControlActivity(findViewById(R.id.controlPanel), this);
        textView.setTextSize(fontSize);
//
        textView.setLineSpacing(1.9f, lineSpacingMultiplier);
        restoreUserSettings();
        filePath = getIntent().getStringExtra("filePath");
        currentPage = appPreferences.getCurrentPage(filePath);

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
               } /*else{
 //               if (!pageOffsetsTemp.isEmpty() && pageOffsetsTemp.size()> 0) {  //双无
                   textView.post(() -> {
                        // 首次分页 → 立即分页并显示第一页
                       new Thread(() -> {
                           Log.w(TAG, "buildPageOffsetsWithCache false");
                           pageOffsets = buildPageOffsetsWithCache(filePath, false);
                        }).start();
                   });
//               }
               }*/
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
//        currentPage   = appPreferences.getCurrentPage(filePath);
//        sentenceIndex = appPreferences.getSentenceIndex(filePath);

        // 如果缓存不存在，则重新估算 pageCharCount 和 textLength
        Log.w(TAG, "  textLength "+textLength);
        Log.w(TAG, "  pageCharCount "+pageCharCount);
        if (textLength <= 0 || pageCharCount <= 0) {
            Log.w(TAG, "TextLength/PageCharCount 无缓存，重新计算");

            int viewWidth = textView.getWidth() - textView.getPaddingLeft() - textView.getPaddingRight();
            int viewHeight = textView.getHeight() - textView.getPaddingTop() - textView.getPaddingBottom();

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
        int viewWidth = textView.getWidth() - textView.getPaddingLeft() - textView.getPaddingRight();
        int viewHeight = textView.getHeight() - textView.getPaddingTop() - textView.getPaddingBottom() - 720;

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

/*
    private int pageCharCount = 2000; // 默认值，加载后会动态估算


/*
    private     int textLength;
    public List<Integer> buildPageOffsets(String filePath) {
//        if(!pageOffsets.isEmpty())pageOffsets.clear();

        int viewWidth = textView.getWidth() - textView.getPaddingLeft() - textView.getPaddingRight();
        int viewHeight = textView.getHeight() - textView.getPaddingTop() - textView.getPaddingBottom();
        //Log.d(TAG, "buildPageOffsets: viewWidth"+viewWidth);
        //Log.d(TAG, "buildPageOffsets: viewHeight"+viewHeight);

        if (viewWidth <= 0 || viewHeight <= 0) return null;
        viewHeight = viewHeight - 780;
        TextPaint paint = textView.getPaint();
        int start = 0;
//        textLength = fullText.length();
        textLength = safeGetTextLength(filePath);
        appPreferences.setTextLength(textLength);
        pageOffsets.add(start);
        //Log.d(TAG, "buildPageOffsets: 11");
        while (start < textLength) {
            int low = start + 1;
            int high = Math.min(textLength, start + 2000);
            int fitPos = start + 1;

            while (low <= high) {
                int mid = (low + high) / 2;
//                String sub = fullText.substring(start, mid);  //从文件读**
                String sub =  readTextSegment(filePath,start, mid);
                StaticLayout layout = android.text.StaticLayout.Builder.obtain(sub, 0, sub.length(), paint, viewWidth)
                        .setLineSpacing(0f, 1.2f).setIncludePad(false).build();
                if (layout.getHeight() <= viewHeight) {
                    fitPos = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
//            //Log.d(TAG, "buildPageOffsets: 22");
            if (fitPos <= start) break;
            pageOffsets.add(fitPos);


            // 如果页数不超过30~100，则克隆到临时分页
            if (pageOffsets.size() > 30)
            { pageOffsetsTemp = new ArrayList<>(pageOffsets); }


            start = fitPos;
        }
//        //Log.d(TAG, "buildPageOffsets: 22");
        totalPages = pageOffsets.size() - 1;
        //Log.d(TAG, "buildPageOffsets: 33");
        seekBar.setMax(Math.max(totalPages, 1));
        //Log.d(TAG, "buildPageOffsets: 44");
        FileUtils.savePageOffsets(this, filePath, pageOffsets);
        return pageOffsets;
    }
*/


    private void restoreUserSettings() {
        speechRate  = appPreferences.getSpeechRate();
        textView.setTextSize(fontSize);
        updateTheme(appPreferences.isDarkTheme());
        setFont(appPreferences.getFontName());

        lastPage = appPreferences.getLastPage();
        lastSentence = appPreferences.getLastSentence();
    }

    private void showLoadingDialog() {
        runOnUiThread(() -> {
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
        });
    }

    private void dismissLoadingDialog() {
        runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
        });
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

            currentPage = lastPage;
            sentenceIndex = lastSentence;

        } catch (Exception e) {
            Log.d(TAG, "loadText: 读取失败");
//            Toast.makeText(this, "读取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//            fullText = "";
        }
    }

/*
    private void loadText(String path) {
        try {
            File file = new File(path);
            String textFilePath = "";

            if (path.toLowerCase().endsWith(".pdf")) {
                textFilePath = path + ".pdftxt";
                File txtFile = new File(textFilePath);
                if (!txtFile.exists()) {
                    taskCompleted = false;
//                    Toast.makeText(this, "首次打开要一些时间，请耐心等待", Toast.LENGTH_LONG).show();
                      textView.setText("首次打开要一些时间，请耐心等待或待会再来，如果看到乱码请退出再试一次");
                    // 后台生成 pdftxt
                    new Thread(() -> {
                        try {
                            FileUtils.extractTextFromPdf(file, this, "fonts/SimsunExtG.ttf");
//                            FileUtils.extractTextFromPdf(file, this,  txtFile);
                            taskCompleted = true;
                            runOnUiThread(() -> Toast.makeText(this, "PDF解析完成", Toast.LENGTH_SHORT).show());
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(() -> Toast.makeText(this, "PDF解析失败", Toast.LENGTH_SHORT).show());
                        }
                    }).start();

                }
                return; // 暂时不进入朗读
            } else if (path.toLowerCase().endsWith(".epub")) {
                textFilePath = path + ".epubtxt";
                File txtFile = new File(textFilePath);
                if (!txtFile.exists()) {
                    taskCompleted = false;
//                    Toast.makeText(this, "首次打开要一些时间，请耐心等待", Toast.LENGTH_LONG).show();
                    textView.setText("首次打开要一些时间，请耐心等待或待会再来，如果看到乱码请退出再试一次");
                    // 后台生成 epubtxt
                    new Thread(() -> {
                        try {
                            FileUtils.extractTextFromEpubByBatch(this, file, txtFile);
                            taskCompleted = true;
//                            runOnUiThread(() -> Toast.makeText(this, "EPUB解析完成", Toast.LENGTH_SHORT).show());
                            Log.d(TAG, "loadText: EPUB解析完成");
//                            return;   //直接打开会乱码，再次打开则OK
                        } catch (Exception e) {
                            e.printStackTrace();
//                            runOnUiThread(() -> Toast.makeText(this, "EPUB解析失败", Toast.LENGTH_SHORT).show());
                            Log.d(TAG, "loadText: EPUB解析失败");
                        }
                    }).start();

                }
                return; // 暂时不进入朗读
            } else {
                textFilePath = path; // 普通文本
            }
            // 已经存在 txt，直接进入朗读
            filePath = textFilePath;
//            fullText = readFileToString(new File(textFilePath));
            taskCompleted = true;
        } catch (Exception e) {
            Toast.makeText(this, "读取失败", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            taskCompleted = false;
        }
        currentPage = lastPage;
        sentenceIndex = lastSentence;
    }

*/

/*    private void loadText(String path) {
        String textFilePath="";
        try {
            File file = new File(path);
            if (path.toLowerCase().endsWith(".pdf")) {
                textFilePath = path + ".pdftxt";
            }else if (path.toLowerCase().endsWith(".epub")) {
                textFilePath = path + ".epubtxt";
            }else{
                textFilePath = path;
            }

            filePath = textFilePath;


        } catch (Exception e) {
            Toast.makeText(this, "读取失败", Toast.LENGTH_SHORT).show();
//            fullText = "";
        }
        currentPage = lastPage;
        sentenceIndex = lastSentence;
    }*/
/*
    private int safeGetTextLength(String filePath) {
        File file = new File(filePath);
        long fileSize = file.length();
        // 假设平均1个字符≈2字节，估算长度
        return (int)Math.min(Integer.MAX_VALUE, fileSize / 2);
    }*/

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


    /** 将文件完整读取为字符串（自动检测编码） */    //这里还是读全文，要改不改
/*
    private String readFileToString(File file) throws IOException {
//            Toast.makeText(this, "7"+file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
//        //Log.d(TAG, "readFileToString: "+file.getAbsolutePath());
        Charset charset = detectEncoding(file);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
//                //Log.d(TAG, "readFileToString: "+line);
                sb.append(line).append("\n");
            }
//                reader.close();
        }

        return sb.toString();
    }


*/


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


    private void loadPage(List<Integer> pageOffsets,int page) {
        Log.d("loadPage", totalPages + " loadPage " + page);
        if (pageOffsets == null || page < 0 || page >= pageOffsets.size()) return;
        if (page < 0 || page >= totalPages) return;

        // 这里不要再用 fullText.length()，改用持久化的 textLength
        textLength = appPreferences.getTextLength(filePath);
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
            int lastSentence = PreferenceManager.getDefaultSharedPreferences(this).getInt("lastSentence", 0);  //改
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
        appPreferences.saveCurrentPage(filePath,currentPage);
        if(currentSentences!=null)
        {
            if (sentenceIndex >= currentSentences.length) {
                // 当前页读完，自动翻页朗读下一页
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    appPreferences.saveCurrentPage(filePath,currentPage);
//                    loadPage(currentPage);
                    Log.d(TAG, "speakNextSentence: loadPage 4"+ pageOffsets.size() );

                    Log.w(TAG, runPageOffsets+" speakNextSentence "+pageOffsets.isEmpty());
                    if (runPageOffsets && pageOffsets.isEmpty() && pageOffsets.size() == 0) {
                        runPageOffsets = false;
                        textView.post(() -> {
                            new Thread(() -> {
                                Log.w(TAG, "buildPageOffsetsWithCache false");
                                pageOffsets = buildPageOffsetsWithCache(filePath, false);
                            }).start();
                        });
                    }
                    loadPage(pageOffsetsTemp != null && !pageOffsetsTemp.isEmpty() ? pageOffsetsTemp : pageOffsets, currentPage);
//                    loadPage( pageOffsets, currentPage);
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
        appPreferences.setLastPage(lastPage);
        appPreferences.setSpeechRate(speechRate);
        appPreferences.setFontSize(fontSize);


        appPreferences.setLastSentence(lastSentence);

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


/*
    private String readTextSegment(String filePath, int start, int end) {
        StringBuilder sb = new StringBuilder();
        File file = new File(filePath);
        Charset charset = detectEncoding(file);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset))) {
            int readCount = 0;
            int c;
            while ((c = reader.read()) != -1) {
                if (readCount >= start && readCount < end) sb.append((char)c);
                readCount++;
                if (readCount >= end) break;
            }
        } catch (IOException e) { e.printStackTrace(); }
//        Log.d(TAG, "readTextSegment: "+ sb.toString());
        return sb.toString();
    }
*/


}
