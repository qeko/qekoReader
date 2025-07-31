package com.qeko.reader;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.qeko.unit.FileAdapter;
import com.qeko.unit.FileItem;
import com.qeko.unit.FileUtils;

import java.io.File;
import java.util.*;

public class MainActivity extends Activity {

    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private List<FileItem> displayItems = new ArrayList<>();
    private Map<File, List<File>> folderMap = new HashMap<>();
    private static final String PREFS_NAME = "reader_prefs";
    private static final String LAST_FILE_PATH = "lastFilePath";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadCachedFolders();

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

        findViewById(R.id.btnScan).setOnClickListener(v -> {
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
            scanDocuments();
        });
    }

    private void scanDocuments() {
        Toast.makeText(this, "📖 正在扫描...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<File> allDocs = FileUtils.scanAll(Environment.getExternalStorageDirectory());
            folderMap.clear();
            for (File file : allDocs) {
                File parent = file.getParentFile();
                folderMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(file);
            }

            displayItems.clear();
            for (Map.Entry<File, List<File>> entry : folderMap.entrySet()) {
                File folder = entry.getKey();
                List<File> files = entry.getValue();

                FileItem folderItem = new FileItem(folder, true);
                folderItem.setDocumentCount(files.size());
                folderItem.setExpanded(true); // 默认展开

                List<FileItem> childItems = new ArrayList<>();
                for (File f : files) {
                    childItems.add(new FileItem(f, false));
                }
                folderItem.setChildren(childItems);
                displayItems.add(folderItem);
            }

            runOnUiThread(() -> {
                adapter.setItems(displayItems);
                Toast.makeText(this, "📚 共找到 " + allDocs.size() + " 本书", Toast.LENGTH_SHORT).show();
            });
        }).start();

        loadCachedFolders();
    }

    private void openFile(File file) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(LAST_FILE_PATH, file.getAbsolutePath()).apply();

        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra("filePath", file.getAbsolutePath());
        startActivity(intent);
    }


    private void loadCachedFolders() {
        File root = Environment.getExternalStorageDirectory();
        List<File> cachedFiles = FileUtils.scanAll(root);
        folderMap.clear();

        // 读取上次阅读路径
        String lastPath = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(LAST_FILE_PATH, "");

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
                if (f.getAbsolutePath().equals(lastPath)) {
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


/*
    private void loadCachedFolders() {
        File root = Environment.getExternalStorageDirectory();
        List<File> cachedFiles = FileUtils.scanAll(root);
        folderMap.clear();

        // 读取上次阅读路径
        String lastPath = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(LAST_FILE_PATH, "");

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
            for (File f : files) {
                FileItem item = new FileItem(f, false);

                if (f.getAbsolutePath().equals(lastPath)) {
                    item.setLastRead(true);
                }

                childItems.add(item);
            }
            folderItem.setChildren(childItems);
            displayItems.add(folderItem);
        }
    }
*/

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
            for (File f : files) {
                FileItem item = new FileItem(f, false);
                childItems.add(item);
            }
            folderItem.setChildren(childItems);
            displayItems.add(folderItem);
        }
    }
*/

/*     private void loadCachedFolders() {
        String lastPath = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(LAST_FILE_PATH, "");

        List<File> cachedFiles = FileUtils.scanAll(Environment.getExternalStorageDirectory());
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
            for (File f : files) {
                FileItem item = new FileItem(f, false);
                if (f.getAbsolutePath().equals(lastPath)) {
                    item.setLastRead(true);
                }
                childItems.add(item);
            }
            folderItem.setChildren(childItems);
            displayItems.add(folderItem);
        }

        adapter.setItems(displayItems);
    }*/
}
