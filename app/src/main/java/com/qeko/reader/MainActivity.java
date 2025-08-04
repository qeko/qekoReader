package com.qeko.reader;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.qeko.openers.FileOpener;
import com.qeko.openers.FileOpenerFactory;
import com.qeko.unit.FileAdapter;
import com.qeko.unit.FileItem;
import com.qeko.unit.FileUtils;
import com.qeko.unit.ScanCacheManager;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));



        adapter = new FileAdapter(displayItems);
        recyclerView.setAdapter(adapter);
        btnBooks = findViewById(R.id.btnBooks);
        btnImages = findViewById(R.id.btnImages);
        btnMusic = findViewById(R.id.btnMusic);
        btnVideo = findViewById(R.id.btnVideo);

        btnBooks.setOnClickListener(v -> switchCategory(new BookFileStrategy(), "BOOK_DIRS"));
        btnImages.setOnClickListener(v -> switchCategory(new ImageFileStrategy(), "IMAGE_DIRS"));
        btnMusic.setOnClickListener(v -> switchCategory(new MusicFileStrategy(), "MUSIC_DIRS"));
        btnVideo.setOnClickListener(v -> switchCategory(new VideoFileStrategy(), "VIDEO_DIRS"));

        ensureStoragePermission();

        adapter.setOnItemClickListener(item -> {
            if (item.isFolder()) {
                item.setExpanded(!item.isExpanded());
                adapter.refreshDisplayItems();
            } else {
//                openFile(item.getFile());
                FileOpener opener = FileOpenerFactory.getOpener(item.getFile().getName());
                opener.open(this, item.getFile());
            }
        });

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

/*        findViewById(R.id.btnScan).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    return;
                }
            } else {
                requestPermissions(new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 1);


            }

            // 默认显示书籍
            switchCategory(new BookFileStrategy(), "BOOK_DIRS");
        });*/
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

        showFiles(files);
    }

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


    private void scanDirectoryRecursive(File dir, Map<String, List<String>> categoryDirs) {
        if (dir == null || !dir.isDirectory() || dir.isHidden()) return;

        // 🧠 判断是否包含目标类型文件
        if (FileUtils.countMatchingFiles(dir, new BookFileStrategy()) > 0) {
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

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(LAST_FILE_PATH, file.getAbsolutePath()).apply();


            String name = file.getName().toLowerCase();

            Intent intent = null;

            if (name.endsWith(".txt")) {
                intent = new Intent(this, ReaderActivity.class);
            } else if (name.endsWith(".pdf")) {
                intent = new Intent(this, PdfReaderActivity.class);
            } else if (name.endsWith(".epub")) {
                intent = new Intent(this, EpubReaderActivity.class);  // 需实现
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


/*
    private void loadCachedFolders() {
        File root = Environment.getExternalStorageDirectory();
        List<File> cachedFiles = FileUtils.scanAll(root);
        folderMap.clear();

        for (File file : cachedFiles) {
            File parent = file.getParentFile();
            folderMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(file);
        }

        displayItems.clear();
        for (Map.Entry<File, List<File>> entry : folderMap.entrySet()) {
            File folder = entry.getKey();
            List<File> files = entry.getValue();

            FileItem folderItem = new FileItem(folder, true);
            folderItem.setDocumentCount(files.size());
            folderItem.setExpanded(true);

            List<FileItem> childItems = new ArrayList<>();
            FileItem lastReadItem = null;

            for (File f : files) {
                FileItem item = new FileItem(f, false);
                if (f.getAbsolutePath().equals(lastFilePath)) {
                    item.setLastRead(true);
                    lastReadItem = item;
                }
                childItems.add(item);
            }

            // 如果找到上次阅读文件，放它到第一位
            if (lastReadItem != null) {
                childItems.remove(lastReadItem);
                childItems.add(0, lastReadItem);
            }

            folderItem.setChildren(childItems);
            displayItems.add(folderItem);
        }

        // 如果需要，把包含上次阅读文件的目录也移到最前面（可选）
        for (int i = 0; i < displayItems.size(); i++) {
            FileItem folderItem = displayItems.get(i);
            List<FileItem> children = folderItem.getChildren();
            if (children != null && !children.isEmpty() && children.get(0).isLastRead()) {
                displayItems.remove(i);
                displayItems.add(0, folderItem);
                break;
            }
        }
    }
*/


    }

    /*

    private void openFile(File file) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(LAST_FILE_PATH, file.getAbsolutePath()).apply();

        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra("filePath", file.getAbsolutePath());
        startActivity(intent);
    }
*/

