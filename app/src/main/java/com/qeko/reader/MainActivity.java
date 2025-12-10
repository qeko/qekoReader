package com.qeko.reader;


import static android.os.FileUtils.*;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.*;

import com.qeko.utils.FileAdapter;
import com.qeko.utils.FileItem;
import com.qeko.utils.FileScanner;
import com.qeko.utils.FileUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private static final int REQ_MANAGE_ALL_FILES = 1002;
    private static final String TAG = "MainActivity";

    private CategoryDirs categoryDirs;
    private FileScanner fileScanner;
    private volatile boolean isScanning = false;

    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private List<FileItem> displayItems = new ArrayList<>();
    private Map<File, List<File>> folderMap = new HashMap<>();

    private Button btnBooks, btnImages, btnMusic, btnVideo, btnScan;
    private EditText etSearch;
    private ImageButton btnClearSearch;
    private TextView tvCountdown;
    private Handler handler = new Handler(Looper.getMainLooper());

    // countdown
    private CountDownTimer countDownTimer;
    private Runnable exitRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // init UI
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter(displayItems);
        recyclerView.setAdapter(adapter);

        btnBooks = findViewById(R.id.btnBooks);
        btnImages = findViewById(R.id.btnImages);
        btnMusic = findViewById(R.id.btnMusic);
        btnVideo = findViewById(R.id.btnVideo);
        btnScan = findViewById(R.id.btnScan);

        etSearch = findViewById(R.id.etSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        tvCountdown = findViewById(R.id.tvCountdown);

        // listeners
        btnBooks.setOnClickListener(v -> switchCategory("BOOK_DIRS"));
        btnImages.setOnClickListener(v -> switchCategory("IMAGE_DIRS"));
        btnMusic.setOnClickListener(v -> switchCategory("MUSIC_DIRS"));
        btnVideo.setOnClickListener(v -> switchCategory("VIDEO_DIRS"));
        btnScan.setOnClickListener(v ->startScan());
//        btnScan.setOnClickListener(v -> scanDocuments());




        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            @Override public void onTextChanged(CharSequence s,int st,int b,int c){
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s){}
        });
        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            adapter.filter("");
            hideKeyboard(v);
        });

        // swipe delete support
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(@NonNull RecyclerView r,@NonNull RecyclerView.ViewHolder h,@NonNull RecyclerView.ViewHolder t){return false;}
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder,int direction){
                int pos = holder.getAdapterPosition();
                FileItem item = adapter.getItemAt(pos);
                if (!item.isFolder()) {
                    File f = item.getFile();
                    if (f.delete()) {
                        adapter.removeItem(pos);
                    } else {
                        Toast.makeText(MainActivity.this,"删除失败",Toast.LENGTH_SHORT).show();
                        adapter.refreshDisplayItems();
                    }
                } else adapter.refreshDisplayItems();
            }
        }).attachToRecyclerView(recyclerView);

        adapter.setOnItemClickListener(item -> {
            if (item.isFolder()) {
                item.setExpanded(!item.isExpanded());
                adapter.refreshDisplayItems();
            } else {
                openFile(item.getFile());
            }
        });

        // permission & init
        categoryDirs = FileUtils.loadCategoryDirs(this);
        if (categoryDirs == null) categoryDirs = new CategoryDirs();

    /*    fileScanner = new FileScanner(this);
        fileScanner.setScanListener(new FileScanner.ScanListener() {
            @Override public void onStart() { runOnUiThread(() -> Toast.makeText(MainActivity.this,"开始扫描",Toast.LENGTH_SHORT).show()); }
            @Override public void onProgress(String currentDir,int scannedDirsCount) { Log.d(TAG,"scan:"+currentDir); }
            @Override public void onDirectoryClassified(String dirPath, Set<String> categories) {}
            @Override public void onComplete(Map<String, List<String>> dirs) {
                // update cache and save
                categoryDirs = FileUtils.mapToCategoryDirs(dirs);
                FileUtils.saveCategoryDirs(MainActivity.this, categoryDirs);
                runOnUiThread(() -> {
                    isScanning = false;
                    Toast.makeText(MainActivity.this,"扫描完成",Toast.LENGTH_SHORT).show();
                    switchCategory("BOOK_DIRS");
                });
            }
            @Override public void onError(String error) { runOnUiThread(() -> Toast.makeText(MainActivity.this,"扫描错误: "+error,Toast.LENGTH_SHORT).show()); }
        });*/

        checkPermissionAndScan();
        switchCategory("BOOK_DIRS");  //需要测试首次与最后一次
//        startScan();
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if (imm!=null) imm.hideSoftInputFromWindow(v.getWindowToken(),0);
    }

    private void checkPermissionAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            if (!Environment.isExternalStorageManager()) {
                // request MANAGE_EXTERNAL_STORAGE permission (user must grant in settings)
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQ_MANAGE_ALL_FILES);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQUEST_STORAGE_PERMISSION);
                return;
            }
        }
        // permission ok -> optionally start scan automatically or wait user
        // we'll not auto-scan here; user uses Scan button. But we can start once:
//         startScan(); // uncomment if you want automatic scan on start
    }

    private void scanDirectoryRecursive(File dir, int depth) {
        if (!isScanning) return;
        if (dir == null || !dir.isDirectory() || dir.isHidden()) return;

        String indent = new String(new char[depth]).replace("\0", "--");
        Log.d(TAG, indent + "scan:" + dir.getAbsolutePath());

        File[] files = dir.listFiles();
        if (files == null) return;

        boolean hasBook = false, hasImage = false, hasMusic = false, hasVideo = false;

        for (File f : files) {
            if (f.isFile()) {
                String name = f.getName().toLowerCase();
                Log.d(TAG, "scan file: " + f.getAbsolutePath());
                if (!hasBook && BookFileStrategy.acceptName(name))
                {
                    hasBook = true;
                    Log.d(TAG, "  -> accepted as book");
                }
                if (!hasImage && ImageFileStrategy.acceptName(name)) hasImage = true;
                if (!hasMusic && MusicFileStrategy.acceptName(name)) hasMusic = true;
                if (!hasVideo && VideoFileStrategy.acceptName(name)) hasVideo = true;
            }
        }

        if (hasBook) categoryDirs.add("BOOK_DIRS", dir);
        if (hasImage) categoryDirs.add("IMAGE_DIRS", dir);
        if (hasMusic) categoryDirs.add("MUSIC_DIRS", dir);
        if (hasVideo) categoryDirs.add("VIDEO_DIRS", dir);

        for (File f : files) {
            if (f.isDirectory()) {
                scanDirectoryRecursive(f, depth + 1);
            }
        }


    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == REQ_MANAGE_ALL_FILES) {
            // user may have granted; check again
            checkPermissionAndScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(req,perms,grants);
        if (req == REQUEST_STORAGE_PERMISSION) {
            if (grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) {
                // ok
            } else {
                Toast.makeText(this,"未授予存储权限，无法扫描",Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** 扫描+分类 */
    private void scanAndClassify() {
        File root = Environment.getExternalStorageDirectory();
        scanDirectoryRecursive(root, 0); // ← 这里也调用
        FileUtils.saveCategoryDirs(this, categoryDirs);
    }

 /*   private void scanDocuments() {
        Toast.makeText(this, "正在扫描，请稍候...", Toast.LENGTH_SHORT).show();
        isScanning = true;

        new Thread(() -> {
            scanAndClassify();

            runOnUiThread(() -> {
                Toast.makeText(this, "扫描完成", Toast.LENGTH_SHORT).show();
                switchCategory("BOOK_DIRS");
            });
        }).start();
    }
*/

    private void startScan() {
        if (isScanning) return;

        isScanning = true;
        Toast.makeText(this, "正在扫描，请稍候...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {

            File root = Environment.getExternalStorageDirectory();
            scanDirectoryRecursive(root, 0);  // ← 这里调用

            FileUtils.saveCategoryDirs(MainActivity.this, categoryDirs);

            runOnUiThread(() -> {
                isScanning = false;
                Toast.makeText(MainActivity.this, "扫描完成", Toast.LENGTH_SHORT).show();
                switchCategory("BOOK_DIRS");
            });

        }).start();
    }


    private void switchCategory(String key) {
        List<File> files;
        switch (key) {
            case "BOOK_DIRS":
                files = FileUtils.reloadWithStrategy(this, new BookFileStrategy(), key);
                break;
            case "IMAGE_DIRS":
                files = FileUtils.reloadWithStrategy(this, new ImageFileStrategy(), key);
                break;
            case "MUSIC_DIRS":
                files = FileUtils.reloadWithStrategy(this, new MusicFileStrategy(), key);
                break;
            case "VIDEO_DIRS":
                files = FileUtils.reloadWithStrategy(this, new VideoFileStrategy(), key);
                break;
            default:
                files = new ArrayList<>();
        }
        Log.d(TAG, key + " found: " + files.size());
        showFiles(files);
    }

    private void showFiles(List<File> files) {
        folderMap.clear();
        List<String> pinnedPaths = loadPinnedFilePaths();

        for (File f: files) {
            File parent = f.getParentFile();
            if (parent==null) parent = new File("/");
            folderMap.computeIfAbsent(parent, k->new ArrayList<>()).add(f);
        }

        displayItems.clear();
        for (Map.Entry<File,List<File>> e: folderMap.entrySet()) {
            File folder = e.getKey();
            List<File> list = e.getValue();
            FileItem folderItem = new FileItem(folder, true);
            folderItem.setDocumentCount(list.size());
            folderItem.setExpanded(true);

            List<FileItem> children = new ArrayList<>();
            for (File f: list) {
                FileItem it = new FileItem(f,false);
                if (pinnedPaths.contains(f.getAbsolutePath())) it.setPinned(true);
                children.add(it);
            }
            Collections.sort(children,(a,b)->{
                if (a.isPinned() && !b.isPinned()) return -1;
                if (!a.isPinned() && b.isPinned()) return 1;
                return a.getFile().getName().compareToIgnoreCase(b.getFile().getName());
            });
            folderItem.setChildren(children);
            displayItems.add(folderItem);
        }

        adapter.setData(displayItems);
        adapter.refreshDisplayItems();
    }

    private List<String> loadPinnedFilePaths() {
        SharedPreferences sp = getSharedPreferences("recent_files",MODE_PRIVATE);
        Set<String> set = sp.getStringSet("pinned_paths", new HashSet<>());
        return new ArrayList<>(set);
    }

    private void savePinnedFilePath(String path) {
        SharedPreferences sp = getSharedPreferences("recent_files",MODE_PRIVATE);
        Set<String> set = sp.getStringSet("pinned_paths", new LinkedHashSet<>());
        if (!(set instanceof LinkedHashSet)) set = new LinkedHashSet<>(set);
        if (set.contains(path)) {
            set.remove(path);
        }
        set.add(path);
        while (set.size()>20) {
            Iterator<String> it=set.iterator();
            if (it.hasNext()) { it.next(); it.remove(); }
        }
        sp.edit().putStringSet("pinned_paths", set).apply();
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

/*
    private void openFile(File file) {
        Log.d(TAG, "openFile: "+file.getAbsolutePath());
        savePinnedFilePath(file.getAbsolutePath());
        String name = file.getName().toLowerCase();
        Intent intent = null;
        if (name.endsWith(".txt") || name.endsWith(".pdf") || name.endsWith(".epub")) {
            // open ReaderActivity if exists, else open with ACTION_VIEW

              intent = new Intent(this, ReaderActivity.class);
            intent.putExtra("filePath", file.getAbsolutePath());

//              intent = new Intent(this, ReaderActivity.class);
            intent.setDataAndType(android.net.Uri.fromFile(file),"text/*");
        } else if (name.endsWith(".mp3") || name.endsWith(".wav")) {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(android.net.Uri.fromFile(file),"audio/*");
        } else if (name.endsWith(".mp4") || name.endsWith(".mkv")) {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(android.net.Uri.fromFile(file),"video/*");
        } else if (ImageFileStrategy.acceptName(name)) {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(android.net.Uri.fromFile(file),"image/*");
        }
        try {
            if (intent!=null) startActivity(intent);
            else Toast.makeText(this,"无法打开类型: "+name,Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this,"打开失败: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }
*/

    // countdown helpers (kept from your original)
    private void startCountdown(long millis) {
        if (countDownTimer!=null) countDownTimer.cancel();
        updateCountdownUi(millis);
        countDownTimer = new CountDownTimer(millis,1000){
            @Override public void onTick(long m){ updateCountdownUi(m); }
            @Override public void onFinish(){ tvCountdown.setText("倒计时结束！"); tvCountdown.setTextColor(Color.BLACK); finishAffinity(); }
        };
        countDownTimer.start();
    }
    private void cancelCountdown() {
        if (countDownTimer!=null) { countDownTimer.cancel(); countDownTimer=null; }
        tvCountdown.setText("倒计时已取消");
        tvCountdown.setTextColor(Color.BLACK);
    }
    private void updateCountdownUi(long millisUntilFinished){
        long total = millisUntilFinished/1000;
        long min = total/60; long sec = total%60;
        tvCountdown.setText(String.format(Locale.getDefault(),"剩余时间：%02d分%02d秒",min,sec));
        if (millisUntilFinished<=10000) tvCountdown.setTextColor(Color.RED);
        else tvCountdown.setTextColor(Color.BLACK);
    }

    @Override protected void onDestroy(){
        if (exitRunnable!=null) handler.removeCallbacks(exitRunnable);
        super.onDestroy();
    }
}
