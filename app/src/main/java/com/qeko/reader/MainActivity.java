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

import com.qeko.utils.AppPreferences;
import com.qeko.utils.FileAdapter;
import com.qeko.utils.FileItem;

import com.qeko.utils.FileUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private static final int REQ_MANAGE_ALL_FILES = 1002;
    private static final String TAG = "MainActivity";

    private CategoryDirs categoryDirs;

    private volatile boolean isScanning = false;

    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private List<FileItem> displayItems = new ArrayList<>();
    private Map<File, List<File>> folderMap = new HashMap<>();

    private Button btnBooks, btnImages, btnMusic, btnVideo, btnScan,btnNewFiles,btnBigFiles,btnRecyclebin,btnConfirm,btnCancel,btnClearAll;
    private EditText etSearch;
    private ImageButton btnClearSearch;
    private TextView tvCountdown;
    private Handler handler = new Handler(Looper.getMainLooper());

    private View panel;
    private RadioGroup timerGroup;
    private RadioGroup radioGroupTime;
    private AppPreferences appPreferences;
    private LinearLayout confirmLayout;

    private long selectedTimeMillis = 0;


    // countdown
    private CountDownTimer countDownTimer;
    private Runnable exitRunnable;

    private static final long ONE_DAY_MS = 3 * 24L * 60 * 60 * 1000;
    private static final long BIG_FILE_SIZE = 100L * 1024 * 1024; // 10MB

    private final List<File> allScannedFiles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        appPreferences = new AppPreferences(this);
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
        btnNewFiles = findViewById(R.id.btnNewFiles);
        btnBigFiles = findViewById(R.id.btnBigFiles);
        etSearch = findViewById(R.id.etSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
//        tvCountdown = findViewById(R.id.tvCountdown);
        btnRecyclebin = findViewById(R.id.btnRecyclebin);


        btnConfirm = findViewById(R.id.btnConfirm);
        btnCancel = findViewById(R.id.btnCancel);
        radioGroupTime = findViewById(R.id.radioGroupTime);
        tvCountdown = findViewById(R.id.tvCountdown);
        btnClearAll = findViewById(R.id.btnClearAll);


//        sp = getSharedPreferences("reader_prefs", MODE_PRIVATE);

        Button btnSetTime = findViewById(R.id.btnSetTime);
        panel = findViewById(R.id.setttime);
        timerGroup = findViewById(R.id.radioGroupTime);

        // 按钮点击后显示/隐藏 RadioGroup 面板
        btnSetTime.setOnClickListener(v -> {
/*            btnClearAll.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();*/
            if (panel.getVisibility() == View.VISIBLE) {
                panel.setVisibility(View.GONE);
            } else {
                panel.setVisibility(View.VISIBLE);
            }
        });

        // listeners
        btnBooks.setOnClickListener(v -> switchCategory("BOOK_DIRS"));
        btnImages.setOnClickListener(v -> switchCategory("IMAGE_DIRS"));
        btnMusic.setOnClickListener(v -> switchCategory("MUSIC_DIRS"));
        btnVideo.setOnClickListener(v -> switchCategory("VIDEO_DIRS"));
 
        btnNewFiles.setOnClickListener(v -> switchCategory("NEW_FILES"));
        btnBigFiles.setOnClickListener(v -> switchCategory("BIG_FILES"));
//        btnRecyclebin.setOnClickListener(v -> switchCategory("RECYCLE_BIN"));
        btnRecyclebin.setOnClickListener(v -> {
            switchCategory("RECYCLE_BIN");
//            adapter.setCategory("RECYCLE_BIN");
            adapter.notifyDataSetChanged();


                if (!adapter.visibleItems.isEmpty()) {
                    btnClearAll.setVisibility(View.VISIBLE);
                }else
                {
                    btnClearAll.setVisibility(View.GONE);
                }


//            btnClearAll.setVisibility(View.VISIBLE); // 👈 显示清空
        });

        btnClearAll.setOnClickListener(v -> {
/*
            if (!"RECYCLE_BIN".equals(adapter.currentCategory)) {
                Toast.makeText(this, "仅能在回收站中清空", Toast.LENGTH_SHORT).show();
                return;
            }
            if (adapter.visibleItems.isEmpty()) {
                Toast.makeText(this, "回收站为空", Toast.LENGTH_SHORT).show();
                return;
            }
*/
           new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("清空回收站")
                    .setMessage("确定永久删除？")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton("确定", (dialog, which) -> {

                        // ✅ 真正清空
                        Iterator<FileItem> it = adapter.visibleItems.iterator();
                        while (it.hasNext()) {
                            FileItem item = it.next();
                            File f = item.getFile();
                            if (f.exists()) {
                                f.delete();
                            }
                            it.remove();
                        }

                        adapter.notifyDataSetChanged();
                        Toast.makeText(this, "回收站已清空", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });


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
      /*  new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(@NonNull RecyclerView r,@NonNull RecyclerView.ViewHolder h,@NonNull RecyclerView.ViewHolder t){return false;}
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder,int direction){
                int pos = holder.getAdapterPosition();
                FileItem item = adapter.getItemAt(pos);
                if (!item.isFolder()) {
                    File f = item.getFile();
//                    if (f.delete()) {
                    Log.d(TAG, "onSwiped: "+adapter.currentCategory);
                    if ("RECYCLE_BIN".equals(adapter.currentCategory)) {
                        if (f.delete()) {
                            adapter.removeItem(pos);
                        } else {
                            Toast.makeText(MainActivity.this,"删除失败",Toast.LENGTH_SHORT).show();
                            adapter.refreshDisplayItems();
                        }
                    }else
                    {
                        if (f.renameTo(new File(f.getAbsoluteFile()+".待删除"))) {
                            adapter.removeItem(pos);
                        } else {
                            Toast.makeText(MainActivity.this,"删除失败",Toast.LENGTH_SHORT).show();
                            adapter.refreshDisplayItems();
                        }
                    }
                } else adapter.refreshDisplayItems();
                adapter.notifyItemRemoved(holder.getAdapterPosition());
            }
        }).attachToRecyclerView(recyclerView);*/
        ItemTouchHelper helper = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                        int pos = holder.getAdapterPosition();
                        FileItem item = adapter.getItemAt(pos);

                        if (item.isFolder()) {
                            adapter.refreshDisplayItems();
                            return;
                        }

                        File f = item.getFile();
                        boolean success = false;

                        // 回收站模式
                        Log.d(TAG, "onSwiped: "+adapter.currentCategory);
                        if ("RECYCLE_BIN".equals(adapter.currentCategory)) {
                            Log.d(TAG, "onSwiped: RECYCLE_BIN");
                            if (direction == ItemTouchHelper.RIGHT) {
                                // 右滑 → 真删除
                                success = f.delete();
                                if (success) adapter.removeItem(pos);
                                else {
                                    Toast.makeText(MainActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                                    adapter.refreshDisplayItems();
                                }
                            } else if (direction == ItemTouchHelper.LEFT) {
                                // 左滑 → 还原
                                success = adapter.restoreAt(pos);
                            }

                        } else { // 普通模式
                            Log.d(TAG, "onSwiped: NORMAL");
                            if (direction == ItemTouchHelper.RIGHT) {
                                // 右滑 → 标记删除
                                File renamed = new File(f.getParentFile(), f.getName() + ".待删除");
                                success = f.renameTo(renamed);
                                if (success) adapter.removeItem(pos);
                                else {
                                    Toast.makeText(MainActivity.this, "标记删除失败", Toast.LENGTH_SHORT).show();
                                    adapter.refreshDisplayItems();
                                }
                            }
                        }

                        if (success) adapter.notifyItemRemoved(pos);
                    }
                }
        );
        helper.attachToRecyclerView(recyclerView);


        ItemTouchHelper helperL = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {


                    @Override
                    public boolean onMove(
                            @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }



                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                        int pos = holder.getAdapterPosition();

                        if ("RECYCLE_BIN".equals(adapter.currentCategory)) {
                            // 👈 左滑：还原
                            adapter.restoreAt(pos);
                        }
                    }
                }
        );


        helper.attachToRecyclerView(recyclerView);


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
        checkPermissionAndScan();
        switchCategory("BOOK_DIRS");  //需要测试首次与最后一次


        btnConfirm = findViewById(R.id.btnConfirm);
        btnCancel = findViewById(R.id.btnCancel);
        radioGroupTime = findViewById(R.id.radioGroupTime);
        tvCountdown = findViewById(R.id.tvCountdown);
//        sp = getSharedPreferences("reader_prefs", MODE_PRIVATE);

        btnSetTime = findViewById(R.id.btnSetTime);
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
        
    }

    private void scanDirectoryRecursive(File dir, int depth) {
        if (!isScanning) return;
        if (dir == null || !dir.isDirectory() || dir.isHidden()) return;

        String indent = new String(new char[depth]).replace("\0", "--");
        Log.d(TAG, indent + "scan:" + dir.getAbsolutePath());

        File[] files = dir.listFiles();
        if (files == null) return;

        boolean hasBook = false, hasImage = false, hasMusic = false, hasVideo = false, hasRecyclebin = false;

        for (File f : files) {
            if (f.isFile()) {
                allScannedFiles.add(f);   // ⭐
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
                if (!hasRecyclebin && RecycleBinStrategy.acceptName(name))
                {
                    hasRecyclebin = true;
                    Log.d(TAG, "  -> accepted as RECYCLE_BIN");
                }
            }
        }

        if (hasBook) categoryDirs.add("BOOK_DIRS", dir);
        if (hasImage) categoryDirs.add("IMAGE_DIRS", dir);
        if (hasMusic) categoryDirs.add("MUSIC_DIRS", dir);
        if (hasVideo) categoryDirs.add("VIDEO_DIRS", dir);
        if (hasRecyclebin) categoryDirs.add("RECYCLE_BIN", dir);

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
                startScan();  //首次扫描
            } else {
                Toast.makeText(this,"未授予存储权限，无法扫描",Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startScan() {
        if (isScanning) return;

        isScanning = true;
        Toast.makeText(this, "请稍候...", Toast.LENGTH_SHORT).show();

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
        adapter.setCategory(key);



        if("RECYCLE_BIN".equals(key))
        btnClearAll.setVisibility(View.VISIBLE); // 👈 显示清空
        else btnClearAll.setVisibility(View.GONE); // 👈 显示清空

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
            case "NEW_FILES":
                long now = System.currentTimeMillis();
//
                files = new ArrayList<>();

                for (File f : allScannedFiles) {
//                    if (now - f.lastModified() <= ONE_DAY_MS) {
                    if (!f.getName().endsWith(".待删除") && now - f.lastModified() <= ONE_DAY_MS) {
                        files.add(f);
                    }
                }
                files.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                break;
            case "BIG_FILES":
                files = new ArrayList<>();
                for (File f : allScannedFiles) {
//                    if (f.length() >= BIG_FILE_SIZE) {
                    if (!f.getName().endsWith(".待删除") && f.length() >= BIG_FILE_SIZE) {
                        files.add(f);
                    }
                }
                files.sort((a, b) -> Long.compare(b.length(), a.length()));
                break;
            case "RECYCLE_BIN":

                files = FileUtils.reloadWithStrategy(this, new RecycleBinStrategy(), key);
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
            if ( name.endsWith(".pdf")|| name.endsWith(".epub")) {
                startBackgroundExtractionDelayed(file);                 //是否可以在这抽取
            }
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


    @Override protected void onDestroy(){
        if (exitRunnable!=null) handler.removeCallbacks(exitRunnable);
        super.onDestroy();
    }
}
