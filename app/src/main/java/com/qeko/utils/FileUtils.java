package com.qeko.utils;
import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import android.content.Context;
import android.content.SharedPreferences;

import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import  nl.siegmann.epublib.domain.Resource;

import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken;
import com.itextpdf.text.pdf.PdfReader;

import com.itextpdf.text.pdf.parser.PdfTextExtractor;

import com.qeko.reader.CategoryDirs;
import com.qeko.reader.FileTypeStrategy;

import java.io.OutputStreamWriter;

import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.universalchardet.UniversalDetector;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Spine;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.epub.EpubReader;

public   class FileUtils {
    private static final String PAGE_OFFSET_EXT = ".pageoffsets";
    private static final String TAG = "FileUtils";
    private static final String PREF_NAME = "category_dirs";


    public static void saveCategoryDirs(Context context, CategoryDirs dirs) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Map<String, List<String>> map = new HashMap<>();
        for (String key : dirs.getAll().keySet()) {
            List<File> files = dirs.getDirs(key);
            List<String> paths = new ArrayList<>();
            for (File f : files) paths.add(f.getAbsolutePath());
            map.put(key, paths);
        }
        sp.edit().putString("json", new Gson().toJson(map)).apply();
    }

    public static CategoryDirs loadCategoryDirs(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = sp.getString("json", null);
        if (json == null) return new CategoryDirs();

        Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
        Map<String, List<String>> map = new Gson().fromJson(json, type);
        return CategoryDirs.fromMap(map);
    }

    // 根据策略重新加载文件
    public static List<File> reloadWithStrategy(Context context, FileTypeStrategy strategy, String key) {
        CategoryDirs dirs = loadCategoryDirs(context);
        List<File> dirList = dirs.getDirs(key);
        List<File> result = new ArrayList<>();
        for (File dir : dirList) {
            if (!dir.exists() || !dir.isDirectory()) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (strategy.accept(f)) {
                    result.add(f);
                }
            }
        }
        return result;
    }

    public static CategoryDirs mapToCategoryDirs(Map<String,List<String>> map) {
        CategoryDirs dirs = new CategoryDirs();
        for (String k: map.keySet()) {
            List<String> arr = map.get(k);
            if (arr == null) continue;
            for (String p: arr) {
                dirs.add(k, new File(p));  // 将 String 转 File
            }
        }
        return dirs;
    }


    /** ---------------------------------------
     *  将 List<File> 保存成字符串
     * ----------------------------------------*/
    private static String toStringList(List<File> files) {
        if (files == null || files.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            sb.append(f.getAbsolutePath()).append(";");
        }
        return sb.toString();
    }

    /** ---------------------------------------
     *  将字符串还原成 List<File>
     * ----------------------------------------*/
    private static void fromStringList(String s, List<File> list) {
        if (s == null || s.trim().isEmpty()) return;

        String[] arr = s.split(";");
        for (String path : arr) {
            if (!path.trim().isEmpty()) {
                list.add(new File(path.trim()));
            }
        }
    }


/*
    public static List<File> reloadWithStrategy(Context ctx, FileTypeStrategy strategy, String cacheKey) {

        CategoryDirs dirs = loadCategoryDirs(ctx);
        List<File> target;

        switch (cacheKey) {
            case "BOOK_DIRS":
                target = dirs.getBookDirs();
                break;

            case "IMAGE_DIRS":
                target = dirs.getImageDirs();
                break;

            case "MUSIC_DIRS":
                target = dirs.getMusicDirs();
                break;

            case "VIDEO_DIRS":
                target = dirs.getVideoDirs();
                break;

            default:
                target = new ArrayList<>();
                break;
        }

        // 清除无效文件或不符合策略的文件
        Iterator<File> it = target.iterator();
        while (it.hasNext()) {
            File f = it.next();
            if (!f.exists() || !strategy.accept(f)) {
                it.remove();
            }
        }

        return target;
    }
*/


    // -------- 文件类型判断 -------- //

    public static boolean isBook(String name) {
        name = name.toLowerCase();
        return name.endsWith(".pdf")
                || name.endsWith(".epub")
                || name.endsWith(".txt")
                || name.endsWith(".mobi");
    }

    public static boolean isImage(String name) {
        name = name.toLowerCase();
        return name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".png")
                || name.endsWith(".webp")
                || name.endsWith(".bmp")
                || name.endsWith(".gif");
    }

    public static boolean isMusic(String name) {
        name = name.toLowerCase();
        return name.endsWith(".mp3")
                || name.endsWith(".aac")
                || name.endsWith(".wav")
                || name.endsWith(".flac")
                || name.endsWith(".m4a");
    }

    public static boolean isVideo(String name) {
        name = name.toLowerCase();
        return name.endsWith(".mp4")
                || name.endsWith(".mkv")
                || name.endsWith(".avi")
                || name.endsWith(".mov")
                || name.endsWith(".wmv")
                || name.endsWith(".flv");
    }

    // -------- 获取扩展名 -------- //

    public static String getExt(String name) {
        int i = name.lastIndexOf(".");
        if (i == -1) return "";
        return name.substring(i).toLowerCase();
    }

    // -------- 安全 listFiles -------- //

    public static File[] safeListFiles(File dir) {
        try {
            return dir.listFiles();
        } catch (Throwable e) {
            Log.e(TAG, "listFiles failed at: " + dir.getAbsolutePath(), e);
            return null;
        }
    }




    // -------- JSON 工具 -------- //

    private static String toJson(java.util.List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        return arr.toString();
    }

    private static java.util.List<String> fromJson(String json) {
        java.util.List<String> list = new java.util.ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
        } catch (JSONException ignored) {}
        return list;
    }


    // ==========================================================
    // 工具函数
    // ==========================================================
    public static Charset detectEncoding(File file) {
        byte[] buf = new byte[4096];
        try (FileInputStream fis = new FileInputStream(file)) {
            UniversalDetector detector = new UniversalDetector(null);
            int nread;
            while ((nread = fis.read(buf)) > 0 && !detector.isDone())
                detector.handleData(buf, 0, nread);
            detector.dataEnd();
            String encoding = detector.getDetectedCharset();
            detector.reset();
            if (encoding != null) return Charset.forName(encoding);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Charset.forName("UTF-8");
    }



    /** 保存分类结果（可选，你原来有就保留） */
    public static void saveCategoryDirs(Context context, Map<String, List<String>> categoryDirs) {
        SharedPreferences sp = context.getSharedPreferences("CATEGORY_DIRS", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();

        for (String key : categoryDirs.keySet()) {
            JSONArray arr = new JSONArray(categoryDirs.get(key));
            editor.putString(key, arr.toString());
        }

        editor.apply();
    }

    public static List<File> getAllDirectories(File root) {
        List<File> dirs = new ArrayList<>();
        if (root != null && root.isDirectory()) {
            dirs.add(root);
            File[] children = root.listFiles();
            if (children != null) {
                for (File f : children) {
                    if (f.isDirectory()) {
                        dirs.addAll(getAllDirectories(f));
                    }
                }
            }
        }
        return dirs;
    }

    public static int countMatchingFiles(File dir, FileTypeStrategy strategy) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {

            for (File file : files) {
                if (file.isDirectory()) {
                    Log.d("TAG", "isDirectory: " + file.getAbsolutePath());
                    count += countMatchingFiles(file, strategy);  // 递归检查子目录
//                } else if (strategy.accept(file)) {
                } else {
                    Log.d("TAG", "getAbsolutePath: " + file.getAbsolutePath());
                    Log.d("TAG", count+"else: " + file.getName());
                    count++;
                }
            }
        }
        return count;
    }



    private static final String CATEGORY_FILE = "category_dirs.json";

    /**
     * 保存分类目录
     */
/*
    public static void saveCategoryDirs(Context context, Map<String, List<String>> categoryDirs) {
        try {
            JSONObject json = new JSONObject();

            for (String key : categoryDirs.keySet()) {
                JSONArray arr = new JSONArray();
                for (String path : categoryDirs.get(key)) {
                    arr.put(path);
                }
                json.put(key, arr);
            }

            FileOutputStream fos = context.openFileOutput(CATEGORY_FILE, Context.MODE_PRIVATE);
            fos.write(json.toString().getBytes());
            fos.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
*/

    /**
     * 判断目录下是否包含目标类型文件
     */
/*    public static int countMatchingFiles(File dir, FileTypeStrategy strategy) {
        int count = 0;

        File[] files = dir.listFiles();
        if (files == null) return 0;

        for (File f : files) {
            if (f.isFile() && strategy.matches(f)) {
                count++;
            }
        }
        return count;
    }
    */

/*
    public static void saveCategoryDirs(Context context, Map<String, List<String>> categoryDirs) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();

        for (Map.Entry<String, List<String>> entry : categoryDirs.entrySet()) {
            String key = entry.getKey();
            List<String> paths = entry.getValue();
//            Log.d("TAG", key+"saveCategoryDirs: "+paths.size());
            editor.putString(key, TextUtils.join(";", paths)); // ✅ 使用分号拼接成单一字符串保存

        }

        editor.apply();
    }
*/


/*    private static List<File> scanDirectory(File dir, FileTypeStrategy strategy) {
        List<File> matched = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && strategy.accept(f)) {
                    matched.add(f);
                }
            }
        }
        return matched;
    }*/

    public static void extractTextFromPdfIncrementalSafe(
            File pdfFile,
            Context context,
            AppPreferences prefs,
            String keyBase
    ) {
        if (pdfFile == null || !pdfFile.exists()) return;

        String outputPath = pdfFile.getAbsolutePath() + ".pdftxt";
        int lastExtractedPage = prefs.getPdfExtractedPage(keyBase); // 获取上次抽取页

        PdfReader reader = null;
        BufferedWriter writer = null;

        try {
            reader = new PdfReader(pdfFile.getAbsolutePath());
            int totalPages = reader.getNumberOfPages();
            Log.d("FileUtils", "PDF总页数: " + totalPages);

            // 已抽取页等于总页数，则无需抽取
            if (lastExtractedPage >= totalPages) {
                Log.d("FileUtils", "PDF已完全抽取，无需重复抽取");
                return;
            }

            // 如果输出文件不存在或者从头抽取，则覆盖
            if (!new File(outputPath).exists() || lastExtractedPage <= 0) {
                lastExtractedPage = 0;
                writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(outputPath, false), StandardCharsets.UTF_8));
            } else {
                // 增量抽取：在原文件基础上追加
                writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(outputPath, true), StandardCharsets.UTF_8));
                Log.d("FileUtils", "从第 " + (lastExtractedPage + 1) + " 页开始增量抽取");
            }

            for (int page = lastExtractedPage + 1; page <= totalPages; page++) {
                String text = PdfTextExtractor.getTextFromPage(reader, page);

                if (text != null && !text.trim().isEmpty()) {
                    writer.write(text);
                    writer.newLine();
                } else {
                    writer.write("[第 " + page + " 页无可提取文本,PDF 存的不是文字，而是 扫描图片]");
                    writer.newLine();
                }

                // 保存当前已抽取页到 AppPreferences
                prefs.savePdfExtractedPage(keyBase, page);

                Log.d("FileUtils", "已抽取页: " + page);
            }

            Log.d("FileUtils", "PDF增量抽取完成，输出路径: " + outputPath);

        } catch (Exception e) {
            Log.e("FileUtils", "PDF抽取失败", e);
        } finally {
            try {
                if (writer != null) writer.close();
            } catch (Exception ignored) {}
            if (reader != null) reader.close();
        }
    }
    public static void extractEpubIncrementalSafe(
            File epubFile,
            File outputTxtFile,
            Context context,
            AppPreferences prefs,
            String keyBase // 用于保存章节抽取进度
    ) {
        try {
            Book book = new EpubReader().readEpub(new FileInputStream(epubFile));
            Spine spine = book.getSpine();
            List<SpineReference> spineRefs = spine.getSpineReferences();
            int totalChapters = spineRefs.size();

            // 获取上次已抽取的章节索引，如果没有则为0
            int startChapter = Math.max(0, prefs.getEpubExtractedChapter(keyBase));

            // 如果全部章节已抽取，则直接返回
            if (startChapter >= totalChapters) {
                Log.d("FileUtils", "EPUB 已全部抽取: " + epubFile.getName());
                return;
            }

            // 如果输出文件不存在，则创建
            if (!outputTxtFile.exists()) {
                outputTxtFile.getParentFile().mkdirs();
                outputTxtFile.createNewFile();
            }

            // 打开文件追加写入
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(outputTxtFile, true), StandardCharsets.UTF_8))) {

                for (int i = startChapter; i < totalChapters; i++) {
                    SpineReference ref = spineRefs.get(i);
                    Resource res = ref.getResource();
                    if (res != null) {
                        try {
                            String html = new String(res.getData(), StandardCharsets.UTF_8);
                            // 去掉 HTML 标签，只保留文本
                            String text = html.replaceAll("<[^>]+>", "\n")
                                    .replaceAll("\\s+", " ")
                                    .trim();
                            if (!text.isEmpty()) {
                                writer.write(text);
                                writer.newLine();
                            }
                        } catch (Exception e) {
                            Log.e("FileUtils", "解析章节失败: " + i, e);
                        }
                    }

                    // 保存当前抽取章节索引
                    prefs.saveEpubExtractedChapter(keyBase, i + 1);
                }
            }

            Log.d("FileUtils", "EPUB 增量抽取完成: " + epubFile.getName());

        } catch (Exception e) {
            Log.e("FileUtils", "extractEpubIncrementalSafe 出错: " + epubFile.getName(), e);
        }
    }

}
