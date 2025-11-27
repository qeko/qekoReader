package com.qeko.reader;

import static android.service.controls.ControlsProviderService.TAG;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;

import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.qeko.tts.TextToSpeechManager;
import com.qeko.utils.AppPreferences;
import com.qeko.utils.FileAdapter;
import com.qeko.utils.FileItem;
import com.qeko.utils.FileUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private List<FileItem> displayItems = new ArrayList<>();
    private Map<File, List<File>> folderMap = new HashMap<>();

    private Button btnBooks, btnImages, btnMusic, btnVideo;
    private Button  btnConfirm,btnCancel;
    private FileTypeStrategy currentStrategy;
    private String currentCacheKey;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable exitRunnable;
    private TextView tvCountdown;

    private View panel;
    private RadioGroup timerGroup;
    private RadioGroup radioGroupTime;
    private AppPreferences appPreferences;
    private LinearLayout confirmLayout;
    private CountDownTimer countDownTimer;
    private long selectedTimeMillis = 0;
    private  EditText etSearch;
    private Map<String, List<String>> categoryDirs = new HashMap<>();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        appPreferences = new AppPreferences(this);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new FileAdapter(displayItems);
        recyclerView.setAdapter(adapter);
        btnBooks = findViewById(R.id.btnBooks);
        btnImages = findViewById(R.id.btnImages);
        btnMusic = findViewById(R.id.btnMusic);
        btnVideo = findViewById(R.id.btnVideo);

        etSearch = findViewById(R.id.etSearch);
        ImageButton btnClearSearch = findViewById(R.id.btnClearSearch);

        btnBooks.setOnClickListener(v -> switchCategory(new BookFileStrategy(), "BOOK_DIRS"));
        btnImages.setOnClickListener(v -> switchCategory(new ImageFileStrategy(), "IMAGE_DIRS"));
        btnMusic.setOnClickListener(v -> switchCategory(new MusicFileStrategy(), "MUSIC_DIRS"));
        btnVideo.setOnClickListener(v -> switchCategory(new VideoFileStrategy(), "VIDEO_DIRS"));
        switchCategory(new BookFileStrategy(), "BOOK_DIRS");
        ensureStoragePermission();

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder v1, @NonNull RecyclerView.ViewHolder v2) { return false; }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                int pos = holder.getAdapterPosition();
                FileItem item = adapter.getItemAt(pos);

                if (!item.isFolder()) {
                    File file = item.getFile();
                    file.delete();     // 删除文件（磁盘）

                    adapter.removeItem(pos);   // 删除内存列表数据
                }
            }
        }).attachToRecyclerView(recyclerView);


        // 输入监听，实时搜索
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

// 清空按钮
        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            adapter.filter(""); // 清空搜索显示所有
            hideKeyboard(v);
        });

        // 隐藏输入面板（软键盘）
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            // 这里 actionDone 或 IME_ACTION_SEARCH 都可
            hideKeyboard(v);
            return true;
        });

// 点击其他区域也隐藏键盘
        recyclerView.setOnTouchListener((v, event) -> {
            hideKeyboard(v);
            return false;
        });


        Button btnScan = findViewById(R.id.btnScan);
        btnScan.setOnClickListener(v -> scanDocuments());
        btnConfirm = findViewById(R.id.btnConfirm);
        btnCancel = findViewById(R.id.btnCancel);
        radioGroupTime = findViewById(R.id.radioGroupTime);
        tvCountdown = findViewById(R.id.tvCountdown);

        Button btnSetTime = findViewById(R.id.btnSetTime);
        panel = findViewById(R.id.setttime);
        timerGroup = findViewById(R.id.radioGroupTime);

        // 按钮点击后显示/隐藏 RadioGroup 面板
        btnSetTime.setOnClickListener(v -> {
            if (panel.getVisibility() == View.VISIBLE) {
                panel.setVisibility(View.GONE);
            } else {
                panel.setVisibility(View.VISIBLE);
            }
        });
        // 设置监听器：点击 RadioButton 时保存并启动倒计时
        timerGroup.setOnCheckedChangeListener((group, checkedId) -> {
            Log.d(TAG, "onCreate:  setOnCheckedChangeListener"+checkedId);
            int minutes = 0;
            if (checkedId == R.id.radio10min) minutes = 10;
            else if (checkedId == R.id.radio30min) minutes = 30;
            else if (checkedId == R.id.radio60min) minutes = 60;
            else if (checkedId == R.id.radio120min) minutes = 120;
            else if (checkedId == R.id.radio0min) cancelCountdown();

            // 取消上一次任务
            if (exitRunnable != null) handler.removeCallbacks(exitRunnable);

            if (minutes > 0) {
                long delay = minutes * 60 * 1000L;

                // 保存退出时间（分钟）
//                sp.edit().putInt("exit_time_min", minutes).apply();

                // 启动新的倒计时任务
                exitRunnable = () -> {
//                    Toast.makeText(this, "时间到，应用即将退出", Toast.LENGTH_SHORT).show();
                    finishAffinity(); // 关闭整个应用
                };
                handler.postDelayed(exitRunnable, delay);

//                Toast.makeText(this, "将在 " + minutes + " 分钟后退出", Toast.LENGTH_SHORT).show();
                startCountdown(  minutes * 60 *  1000L);
            } else {
//                sp.edit().remove("exit_time_min").apply();
//                Toast.makeText(this, "已取消定时退出", Toast.LENGTH_SHORT).show();
            }

        });



        btnConfirm.setOnClickListener(v -> {
            int checkedId = radioGroupTime.getCheckedRadioButtonId();
            if (checkedId == -1) {
                Toast.makeText(this, "请选择时间", Toast.LENGTH_SHORT).show();
                return;
            }


            if (checkedId == R.id.radio10min) selectedTimeMillis = 1;
            else if (checkedId == R.id.radio30min) selectedTimeMillis = 3;
            else if (checkedId == R.id.radio60min) selectedTimeMillis = 60;
            else if (checkedId == R.id.radio120min) selectedTimeMillis = 120;

            startCountdown(selectedTimeMillis);
            radioGroupTime.setVisibility(View.GONE);
            confirmLayout.setVisibility(View.GONE);
        });

        btnCancel.setOnClickListener(v -> {
            radioGroupTime.setVisibility(View.GONE);
            confirmLayout.setVisibility(View.GONE);
            cancelCountdown();
        });


        initCategory();
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        etSearch.clearFocus(); // 失去焦点
    }


    private void startCountdown(long millis) {
        // 先取消已有计时器，防止重复
        cancelCountdown();

        // 立即显示初始剩余时间（onTick 第一次会在 interval 之后调用）
        updateCountdownUi(millis);

        // 每 1s 回调一次
        countDownTimer = new CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // 每次回调用传入的剩余毫秒数更新 UI
                updateCountdownUi(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("倒计时结束！");
                tvCountdown.setTextColor(Color.BLACK);
                // 如果需要在结束时做其他操作，在这里添加
                finishAffinity();
            }
        };

        countDownTimer.start();
    }

    private void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        tvCountdown.setText("倒计时已取消");
        tvCountdown.setTextColor(Color.BLACK);
    }

    private void updateCountdownUi(long millisUntilFinished) {
        long totalSeconds = millisUntilFinished / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        // 格式化显示为“剩余时间：mm分ss秒”
        String timeStr = String.format(Locale.getDefault(), "剩余时间：%02d分%02d秒", minutes, seconds);
        tvCountdown.setText(timeStr);

        // 小于10秒时变红提醒
        if (millisUntilFinished <= 10_000) {
            tvCountdown.setTextColor(Color.RED);
        } else {
            tvCountdown.setTextColor(Color.BLACK);
        }
    }



    private void startExitCountdown(int minutes) {
        if (exitRunnable != null) handler.removeCallbacks(exitRunnable);
        exitRunnable = () -> {
            Toast.makeText(this, "时间到，应用即将退出", Toast.LENGTH_SHORT).show();
            finishAffinity();
        };
        handler.postDelayed(exitRunnable, minutes * 60 * 1000L);
    }



    private List<File> loadImageFiles() {
        // TODO: 递归扫描或读取保存的图片目录文件列表
        return new ArrayList<>();
    }


    private void ensureStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION);
            }
        }
    }


    private void switchCategory(FileTypeStrategy strategy, String cacheKey) {
        this.currentStrategy = strategy;
        this.currentCacheKey = cacheKey;
        List<File> files = FileUtils.reloadWithStrategy(this, strategy, cacheKey);
        showFiles(files);
    }


    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".gif") ||
                name.endsWith(".bmp") || name.endsWith(".webp");
    }

    private void showFiles(List<File> files) {
        folderMap.clear();

        // 读取最近访问文件列表（示例，需你实现）
        List<String> pinnedPaths = loadPinnedFilePaths();

        for (File file : files) {
            File parent = file.getParentFile();
            folderMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(file);

            startBackgroundExtractionDelayed(file);                 //是否可以在这抽取
        }

        displayItems.clear();
        for (Map.Entry<File, List<File>> entry : folderMap.entrySet()) {
            File folder = entry.getKey();
            List<File> filesInFolder = entry.getValue();

            FileItem folderItem = new FileItem(folder, true);
            folderItem.setDocumentCount(filesInFolder.size());
            folderItem.setExpanded(true);

            List<FileItem> childItems = new ArrayList<>();
            for (File f : filesInFolder) {
                FileItem item = new FileItem(f, false);

                 if (!isImageFile(f) && pinnedPaths.contains(f.getAbsolutePath())) {
                    item.setPinned(true);
                }
                childItems.add(item);
            }

            // 置顶文件排序
            Collections.sort(childItems, (a, b) -> {
                if (a.isPinned() && !b.isPinned()) return -1;
                else if (!a.isPinned() && b.isPinned()) return 1;
                else return a.getFile().getName().compareToIgnoreCase(b.getFile().getName());
            });

            folderItem.setChildren(childItems);
            displayItems.add(folderItem);
        }

        adapter.setData(displayItems);

        adapter = new FileAdapter(displayItems);
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(item -> {
            if (item.isFolder()) {
                item.setExpanded(!item.isExpanded());
                adapter.refreshDisplayItems();
            } else {
                openFile(item.getFile());
            }
        });
    }


    private List<String> loadPinnedFilePaths() {
        SharedPreferences sp = getSharedPreferences("recent_files", MODE_PRIVATE);
        Set<String> pinnedSet = sp.getStringSet("pinned_paths", new HashSet<>());
        return new ArrayList<>(pinnedSet);
    }

    private void savePinnedFilePath(String path) {
        SharedPreferences sp = getSharedPreferences("recent_files", MODE_PRIVATE);
        // 用 LinkedHashSet 保证顺序，且避免重复
        Set<String> pinnedSet = sp.getStringSet("pinned_paths", new LinkedHashSet<>());
        if (!(pinnedSet instanceof LinkedHashSet)) {
            pinnedSet = new LinkedHashSet<>(pinnedSet);
        }

        // 如果已有该路径，先移除再添加，保证最新
        if (pinnedSet.contains(path)) {
            pinnedSet.remove(path);
        }
        pinnedSet.add(path);

        // 超过5个时，删除最旧的
        while (pinnedSet.size() > 5) {
            // LinkedHashSet 没有索引，只能迭代删除第一个元素
            Iterator<String> it = pinnedSet.iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }

        // 保存回 SharedPreferences
        sp.edit()
                .putStringSet("pinned_paths", pinnedSet)
                .apply();
    }


    private void initCategory() {
        // 先尝试加载缓存
        categoryDirs = FileUtils.loadCategoryDirs(this);

        if (categoryDirs == null || categoryDirs.isEmpty()) {
            scanDocuments();
        } else {
            switchCategory(currentStrategy, currentCacheKey);
        }
    }


    private void scanDocuments() {
        Toast.makeText(this, "正在扫描，请稍候...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            // 初始化四类策略及其对应缓存键
            Map<FileTypeStrategy, String> strategyMap = new HashMap<>();
            strategyMap.put(new BookFileStrategy(), "BOOK_DIRS");
            strategyMap.put(new ImageFileStrategy(), "IMAGE_DIRS");
            strategyMap.put(new MusicFileStrategy(), "MUSIC_DIRS");
            strategyMap.put(new VideoFileStrategy(), "VIDEO_DIRS");

            // 初始化缓存结果结构
            Map<String, List<File>> resultDirs = new HashMap<>();
            for (String key : strategyMap.values()) {
                resultDirs.put(key, new ArrayList<>());
            }
            // 一次遍历扫描
            scanAndClassify();

            runOnUiThread(() -> {
                Toast.makeText(this, "扫描完成", Toast.LENGTH_SHORT).show();
                switchCategory(currentStrategy, currentCacheKey);
            });
        }).start();
    }

    private void scanAndClassify() {
        File root = Environment.getExternalStorageDirectory();
         categoryDirs.put("BOOK_DIRS", new ArrayList<>());
        categoryDirs.put("IMAGE_DIRS", new ArrayList<>());
        categoryDirs.put("MUSIC_DIRS", new ArrayList<>());
        categoryDirs.put("VIDEO_DIRS", new ArrayList<>());
        scanDirectoryRecursive(root, categoryDirs);
        FileUtils.saveCategoryDirs(this, categoryDirs);
//        Toast.makeText(this, "分类目录扫描完成", Toast.LENGTH_SHORT).show();
    }


    private void scanDirectoryRecursive(File dir, Map<String, List<String>> categoryDirs) {
        if (dir == null || !dir.isDirectory() || dir.isHidden()) return;

        // 🧠 判断是否包含目标类型文件
        if (FileUtils.countMatchingFiles(dir, new BookFileStrategy()) > 0) {
 /*            pdfList = (ArrayList<File>) scanFiles(dir, new String[]{".pdf",".epub"});

            if(pdfList !=null && pdfList.size() > 0) {
                FileUtils.processPdfListInBackground(pdfList, MainActivity.this);
            }*/

//            pdfList =  (ArrayList<File>)scanFiles(dir, new String[]{".epub"});

            categoryDirs.get("BOOK_DIRS").add(dir.getAbsolutePath());
        }
        if (FileUtils.countMatchingFiles(dir, new ImageFileStrategy()) > 0) {
            categoryDirs.get("IMAGE_DIRS").add(dir.getAbsolutePath());
        }
        if (FileUtils.countMatchingFiles(dir, new MusicFileStrategy()) > 0) {
            categoryDirs.get("MUSIC_DIRS").add(dir.getAbsolutePath());
        }
        if (FileUtils.countMatchingFiles(dir, new VideoFileStrategy()) > 0) {
            categoryDirs.get("VIDEO_DIRS").add(dir.getAbsolutePath());
        }

        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    scanDirectoryRecursive(child, categoryDirs); // 🔁 递归
                }
            }
        }
    }

    private void openFile(File file) {
            savePinnedFilePath(file.getAbsolutePath());
            String name = file.getName().toLowerCase();

            Intent intent = null;
        if (name.endsWith(".txt")|| name.endsWith(".pdf")|| name.endsWith(".epub")) {
//            if (name.endsWith(".txt")) {
                intent = new Intent(this, ReaderActivity.class);
//            } else if (name.endsWith(".pdf")) {
//                intent = new Intent(this, PdfReaderActivity.class);
//            } else if (name.endsWith(".epub")) {
//                intent = new Intent(this, EpubReaderActivity.class);  // 需实现
//            } else if (name.endsWith(".mobi")) {
//                intent = new Intent(this, MobiReaderActivity.class);  // 需实现
//            } else if (name.endsWith(".azw") || name.endsWith(".azw3")) {
//                intent = new Intent(this, KindleReaderActivity.class); // 需实现
            } else if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac")) {
                intent = new Intent(this, MusicPlayerActivity.class);
            } else if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mpg")) {
                intent = new Intent(this, VideoPlayerActivity.class);
            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".bmp") || name.endsWith(".gif")) {
                intent = new Intent(this, ImageViewerActivity.class);
            }
        Log.d(TAG, "openFile: file.getAbsolutePath()="+file.getAbsolutePath());
            if (intent != null) {
                intent.putExtra("filePath", file.getAbsolutePath());
                startActivity(intent);
            } else {
                Toast.makeText(this, "无法打开该类型的文件: " + name, Toast.LENGTH_SHORT).show();
            }
        }


    public List<File> scanFiles(File dir, String[] extensions) {
        List<File> result = new ArrayList<>();
        if (dir == null || !dir.exists()) {
            return result;
        }
        if (dir.isFile()) {
            String nameLower = dir.getName().toLowerCase(Locale.ROOT);
            for (String ext : extensions) {
                if (nameLower.endsWith(ext.toLowerCase(Locale.ROOT))) {
                    result.add(dir);
                    break; // 匹配到一个扩展名即可
                }
            }
            return result;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return result;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                result.addAll(scanFiles(file, extensions)); // 递归
            } else {
                String nameLower = file.getName().toLowerCase(Locale.ROOT);
                for (String ext : extensions) {
                    if (nameLower.endsWith(ext.toLowerCase(Locale.ROOT))) {
                        result.add(file);
                        break;
                    }
                }
            }
        }
        return result;
    }

    public static ArrayList<File> scanPdfFiles(File dir, String extension) {
        ArrayList<File> resultFiles = new ArrayList<>();
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return resultFiles;
        }
        scanRecursive(dir, resultFiles, extension.toLowerCase());
        return resultFiles;
    }

    private static void scanRecursive(File folder, ArrayList<File> resultFiles, String extension) {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scanRecursive(f, resultFiles, extension);  // 递归子目录
            } else if (f.getName().toLowerCase().endsWith(extension)) {
                resultFiles.add(f);
            }
        }
    }

    private void startBackgroundExtractionDelayed(File file) {
        // 延迟 10 秒后执行抽取任务
        handler.postDelayed(() -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                String path = file.getAbsolutePath();
                if (path.toLowerCase().endsWith(".pdf")) {
                    FileUtils.extractTextFromPdfIncrementalSafe(
                            file,
                            MainActivity.this,
                            appPreferences,
                            path
                    );
                } else if (path.toLowerCase().endsWith(".epub")) {
                    String textFilePath = path + ".epubtxt";
                    FileUtils.extractEpubIncrementalSafe(
                            file,
                            new File(textFilePath),
                            MainActivity.this,
                            appPreferences,
                            path
                    );
                }
            });
        }, 10_000); // ← 延迟 10 秒执行
    }


    @Override
    protected void onDestroy() {
        if (exitRunnable != null) {
            handler.removeCallbacks(exitRunnable);
        }
        super.onDestroy();
    }
}