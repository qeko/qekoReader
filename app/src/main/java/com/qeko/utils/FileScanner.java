package com.qeko.utils;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.qeko.reader.BookFileStrategy;
import com.qeko.reader.ImageFileStrategy;
import com.qeko.reader.MusicFileStrategy;
import com.qeko.reader.VideoFileStrategy;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-performance filesystem scanner.
 * BFS-style queue, each directory listFiles() called once.
 */
public class FileScanner {
    private static final String TAG = "FileScanner";
    public static final String KEY_BOOKS = "BOOK_DIRS";
    public static final String KEY_IMAGES = "IMAGE_DIRS";
    public static final String KEY_MUSIC = "MUSIC_DIRS";
    public static final String KEY_VIDEO = "VIDEO_DIRS";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private final Context context;
    private ScanListener listener;

    public FileScanner(Context ctx) { this.context = ctx.getApplicationContext(); }

    public void setScanListener(ScanListener l) { this.listener = l; }

    public boolean isScanning() { return isScanning.get(); }

    public void startScan(File root) {
        if (root==null || !root.isDirectory()) {
            if (listener!=null) postError("Invalid root");
            return;
        }
        if (!isScanning.compareAndSet(false,true)) {
            if (listener!=null) postError("Already scanning");
            return;
        }
        executor.execute(() -> {
            if (listener!=null) postStart();
            Map<String, List<String>> result = new HashMap<>();
            result.put(KEY_BOOKS,new ArrayList<>());
            result.put(KEY_IMAGES,new ArrayList<>());
            result.put(KEY_MUSIC,new ArrayList<>());
            result.put(KEY_VIDEO,new ArrayList<>());

            Queue<File> q = new LinkedList<>();
            q.offer(root);
            int scanned = 0;
            while (!q.isEmpty() && isScanning.get()) {
                File dir = q.poll();
                if (dir==null) continue;
                if (!dir.isDirectory()) continue;
                if (dir.isHidden()) continue;
                File[] children = safeListFiles(dir);
                if (children==null) continue;
                boolean hasBook=false, hasImage=false, hasMusic=false, hasVideo=false;
                for (File f: children) {
                    if (!isScanning.get()) break;
                    if (f.isDirectory()) { q.offer(f); continue; }
                    String name = f.getName().toLowerCase(Locale.US);
                    if (!hasBook && BookFileStrategy.acceptName(name)) hasBook=true;
                    if (!hasImage && ImageFileStrategy.acceptName(name)) hasImage=true;
                    if (!hasMusic && MusicFileStrategy.acceptName(name)) hasMusic=true;
                    if (!hasVideo && VideoFileStrategy.acceptName(name)) hasVideo=true;
                    if (hasBook && hasImage && hasMusic && hasVideo) break;
                }
                if (hasBook) addUnique(result.get(KEY_BOOKS), dir.getAbsolutePath());
                if (hasImage) addUnique(result.get(KEY_IMAGES), dir.getAbsolutePath());
                if (hasMusic) addUnique(result.get(KEY_MUSIC), dir.getAbsolutePath());
                if (hasVideo) addUnique(result.get(KEY_VIDEO), dir.getAbsolutePath());
                scanned++;
                if (listener!=null) postProgress(dir.getAbsolutePath(), scanned);
            }
            isScanning.set(false);
            if (listener!=null) postComplete(result);
        });
    }

    public void stopScan() { isScanning.set(false); }

    private void addUnique(List<String> list, String path) {
        synchronized (list) { if (!list.contains(path)) list.add(path); }
    }

    private File[] safeListFiles(File dir) {
        try { return dir.listFiles(); }
        catch (Throwable t) { Log.w(TAG,"listFiles fail: "+dir.getAbsolutePath(),t); return null;}
    }

    private void postStart(){ mainHandler.post(() -> { if (listener!=null) listener.onStart(); }); }
    private void postProgress(String cur,int c){ mainHandler.post(() -> { if (listener!=null) listener.onProgress(cur,c); }); }
    private void postComplete(Map<String,List<String>> map){ mainHandler.post(() -> { if (listener!=null) listener.onComplete(map); }); }
    private void postError(String e){ mainHandler.post(() -> { if (listener!=null) listener.onError(e); }); }

    public interface ScanListener {
        void onStart();
        void onProgress(String currentDir,int scannedDirsCount);
        default void onDirectoryClassified(String dirPath, Set<String> categories){}
        void onComplete(Map<String,List<String>> categorizedDirs);
        void onError(String error);
    }
}
