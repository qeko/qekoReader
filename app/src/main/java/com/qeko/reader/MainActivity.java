package com.qeko.reader;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.bumptech.glide.Glide;
import com.qeko.utils.AppPreferences;
import com.qeko.utils.FileAdapter;
import com.qeko.utils.FileItem;
import com.qeko.utils.FileUtils;
import com.qeko.utils.ScanCacheManager;

import java.io.File;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private List<FileItem> displayItems = new ArrayList<>();
    private Map<File, List<File>> folderMap = new HashMap<>();
    private static final String PREFS_NAME = "scan_cache";
    private static final String LAST_FILE_PATH = "lastFilePath";
    private Button btnBooks, btnImages, btnMusic, btnVideo;
    private FileTypeStrategy currentStrategy;
    private String currentCacheKey;
    private Spinner spinner;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable exitRunnable;

    private ControlActivity controlActivity;

    private RecyclerView rvImages;
    private Button btnSwitchView;
    private boolean isGrid = true; // 当前是否为网格视图

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

//        rvImages = findViewById(R.id.rvImages);
//        btnSwitchView = findViewById(R.id.btnSwitchView);

        adapter = new FileAdapter(displayItems);
        recyclerView.setAdapter(adapter);
        btnBooks = findViewById(R.id.btnBooks);
        btnImages = findViewById(R.id.btnImages);
        btnMusic = findViewById(R.id.btnMusic);
        btnVideo = findViewById(R.id.btnVideo);
//        btnSetting = findViewById(R.id.btnSetting);



        btnBooks.setOnClickListener(v -> switchCategory(new BookFileStrategy(), "BOOK_DIRS"));
        btnImages.setOnClickListener(v -> switchCategory(new ImageFileStrategy(), "IMAGE_DIRS"));
        btnMusic.setOnClickListener(v -> switchCategory(new MusicFileStrategy(), "MUSIC_DIRS"));
        btnVideo.setOnClickListener(v -> switchCategory(new VideoFileStrategy(), "VIDEO_DIRS"));
        switchCategory(new BookFileStrategy(), "BOOK_DIRS");
//        controlActivity = new ControlActivity(findViewById(R.id.controlPanel), this);

//        btnSetting.setOnClickListener(v -> controlActivity.toggleVisibility());


//        controlActivity.toggleVisibility();
        ensureStoragePermission();



        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder v1, @NonNull RecyclerView.ViewHolder v2) { return false; }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                int pos = holder.getAdapterPosition();
                FileItem item = adapter.getItemAt(pos);
                if (!item.isFolder()) {
                    File file = item.getFile();
                    file.delete();
//                    scanDocuments(); // 不要refresh
                }
            }
        }).attachToRecyclerView(recyclerView);

        Button btnScan = findViewById(R.id.btnScan);
        btnScan.setOnClickListener(v -> scanDocuments());

        startExitTimer();
    }

    private void startExitTimer() {
        AppPreferences prefs = new AppPreferences(this);
        long exitTime = prefs.getExitTime();
        if (exitTime > 0) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                finishAffinity(); // 关闭整个App
                System.exit(0);
            }, exitTime);
        }
    }


    private List<File> loadImageFiles() {
        // TODO: 递归扫描或读取保存的图片目录文件列表
        return new ArrayList<>();
    }

    public List<File> reloadWithStrategy(Context context, FileTypeStrategy strategy, String cacheKey) {
        Set<String> cachedDirs = ScanCacheManager.getCachedDirs(context, cacheKey);
        Set<String> updatedDirs = new HashSet<>();
        List<File> result = new ArrayList<>();

        for (String path : cachedDirs) {
            File dir = new File(path);
            List<File> files = FileUtils.scanFilesIn(dir, strategy); // 不递归
            if (!files.isEmpty()) {
                updatedDirs.add(path);
                result.addAll(files);
            }
        }

        // 更新缓存（去掉已无文档的目录）
        ScanCacheManager.saveCachedDirs(context, cacheKey, updatedDirs);
        return result;
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

/*
    private void reloadWithStrategy(FileTypeStrategy strategy) {
        File root = Environment.getExternalStorageDirectory();
        List<File> filteredFiles = FileUtils.scanAll(root, strategy);
        folderMap.clear();
        for (File file : filteredFiles) {
            File parent = file.getParentFile();
            folderMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(file);
        }
        displayItems.clear();
        for (Map.Entry<File, List<File>> entry : folderMap.entrySet()) {
            File folder = entry.getKey();
            List<File> files = entry.getValue();
            FileItem folderItem = new FileItem(folder, true);
            folderItem.setExpanded(true);
            List<FileItem> children = new ArrayList<>();
            for (File f : files) {
                FileItem item = new FileItem(f, false);
                children.add(item);
            }
            folderItem.setChildren(children);
            displayItems.add(folderItem);
        }
        adapter.setItems(displayItems);
    }
*/

    private void switchCategory(FileTypeStrategy strategy, String cacheKey) {
        this.currentStrategy = strategy;
        this.currentCacheKey = cacheKey;
        List<File> files = FileUtils.reloadWithStrategy(this, strategy, cacheKey);



/*        ImageView  icon = findViewById(R.id.icon);
            if ("IMAGE_DIRS".equals(cacheKey)) {
                ViewGroup.LayoutParams params = icon.getLayoutParams();
                params.width = params.width * 13;   // 放大3倍
                params.height = params.height * 13; // 放大3倍
                icon.setLayoutParams(params);
        }*/


        showFiles(files);


    }

/*
    private void showFiles(List<File> files) {
        folderMap.clear();
        for (File file : files) {
            File parent = file.getParentFile();
            folderMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(file);
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
                应该是在这里处理 读出保存的最后文件进行比较，如果相等则 显示红色，带 上ic-pin 图标，
                不太清楚置顶要如何处理？
                childItems.add(item);
            }
            folderItem.setChildren(childItems);
            displayItems.add(folderItem);
        }

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
*/

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


                // 判断是否图片（仅当 ImageFileStrategy 时）
                // 如果是图片文件，直接加载缩略图
//                if (isImageFile(f)) {
//                    item.. icon.setScaleX(1f); // 确保不被放大
//                    icon.setScaleY(1f);
////                    item.setUseThumbnail(true);
//                    layoutParams.width *= 3;
//                    layoutParams.height *= 3;
//                    imageView.setLayoutParams(layoutParams);
//                }
//                if (switchCategory == IMAGE_DIRS) {
//                    icon.setScaleX(3.0f);
//                    icon.setScaleY(3.0f);
//                }
//                if (switchCategory == IMAGE_DIRS) {
//                    ViewGroup.LayoutParams params = icon.getLayoutParams();
//                    params.width = params.width * 3;   // 放大3倍
//                    params.height = params.height * 3; // 放大3倍
//                    icon.setLayoutParams(params);
//                }
/*                if (isImageFile(f)) {
                    // 使用 Glide 加载缩略图
                    Glide.with(this)
                            .load(f)
                            .placeholder(R.drawable.ic_image_placeholder) // 占位图
                            .centerCrop();
//                            .into(item.getIconImageView()); // 你的 Item 里要有 ImageView 引用
                }*/


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

/*
    // 打开文件后保存最近访问文件路径示例
    private void savePinnedFilePath(String path) {
        SharedPreferences sp = getSharedPreferences("recent_files", MODE_PRIVATE);
        Set<String> pinnedSet = sp.getStringSet("pinned_paths", new HashSet<>());
        pinnedSet.add(path);
        sp.edit().putStringSet("pinned_paths", pinnedSet);//.apply();
    }
*/

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
        Map<String, List<String>> categoryDirs = new HashMap<>();
        categoryDirs.put("BOOK_DIRS", new ArrayList<>());
        categoryDirs.put("IMAGE_DIRS", new ArrayList<>());
        categoryDirs.put("MUSIC_DIRS", new ArrayList<>());
        categoryDirs.put("VIDEO_DIRS", new ArrayList<>());

        scanDirectoryRecursive(root, categoryDirs);

        FileUtils.saveCategoryDirs(this, categoryDirs);
//        Toast.makeText(this, "分类目录扫描完成", Toast.LENGTH_SHORT).show();
    }

private        ArrayList<File> pdfList;
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
            } else if (name.endsWith(".mobi")) {
                intent = new Intent(this, MobiReaderActivity.class);  // 需实现
            } else if (name.endsWith(".azw") || name.endsWith(".azw3")) {
                intent = new Intent(this, KindleReaderActivity.class); // 需实现
            } else if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac")) {
                intent = new Intent(this, MusicPlayerActivity.class);
            } else if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mpg")) {
                intent = new Intent(this, VideoPlayerActivity.class);
            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".bmp") || name.endsWith(".gif")) {
                intent = new Intent(this, ImageViewerActivity.class);
            }

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
/*    public static ArrayList<File> scanPdfFiles(File dir) {
        ArrayList<File> pdfFiles = new ArrayList<>();
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return pdfFiles;
        }
        scanRecursive(dir, pdfFiles);
        return pdfFiles;
    }

    private static void scanRecursive(File folder, ArrayList<File> pdfFiles) {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scanRecursive(f, pdfFiles);  // 递归子目录
            } else if (f.getName().toLowerCase().endsWith(".pdf")) {
                pdfFiles.add(f);
            }
        }
    }*/

    @Override
    protected void onDestroy() {
        if (exitRunnable != null) {
            handler.removeCallbacks(exitRunnable);
        }
        super.onDestroy();
    }
}