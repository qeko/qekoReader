package com.qeko.utils;

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
import com.itextpdf.text.pdf.PdfReader;

import com.itextpdf.text.pdf.parser.PdfTextExtractor;

import com.qeko.reader.FileTypeStrategy;

import java.io.OutputStreamWriter;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.universalchardet.UniversalDetector;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Spine;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.epub.EpubReader;

public class FileUtils {
    private static final String PAGE_OFFSET_EXT = ".pageoffsets";

    // 递归扫描 .txt 文件
    public static List<File> scanAll(File dir, FileTypeStrategy strategy) {
        List<File> result = new ArrayList<>();
        if (dir == null || !dir.exists()) return result;

        File[] files = dir.listFiles();
        if (files == null) return result;

        for (File f : files) {
            if (f.isDirectory()) {
                result.addAll(scanAll(f, strategy));
            } else if (strategy.accept(f)) {
                result.add(f);
            }
        }
        return result;
    }


    // 回调接口
    public interface ExtractProgressCallback {
        void onProgress(int progress);   // 0..100
        void onDone();
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


    public static List<File> reloadWithStrategy(Context context, FileTypeStrategy strategy, String cacheKey) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String cachedPaths = prefs.getString(cacheKey, "");  // ✅ 使用 getString

//        Toast.makeText(context, "reloadWithStrategy="+cachedPaths, Toast.LENGTH_SHORT).show(); //读出来是0

        List<File> result = new ArrayList<>();
        if (!cachedPaths.isEmpty()) {
            for (String path : cachedPaths.split(";")) {
                File dir = new File(path);
                if (dir.exists()) {
                    result.addAll(scanDirectory(dir, strategy));
                }
            }
        }
        return result;
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
                    Log.d("TAG", "else: " + file.getName());
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

    /**
     * 读取分类目录缓存
     */
    public static Map<String, List<String>> loadCategoryDirs(Context context) {

        Map<String, List<String>> result = new HashMap<>();

        try {
            File file = new File(context.getFilesDir(), CATEGORY_FILE);
            if (!file.exists()) {
                return result; // 返回空，表示需要扫描
            }

            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }

            fis.close();
            String jsonStr = bos.toString();

            JSONObject json = new JSONObject(jsonStr);

            for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONArray arr = json.getJSONArray(key);
                List<String> list = new ArrayList<>();

                for (int i = 0; i < arr.length(); i++) {
                    list.add(arr.getString(i));
                }

                result.put(key, list);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result; // 即使异常，也返回空 map
    }


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


    private static List<File> scanDirectory(File dir, FileTypeStrategy strategy) {
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
    }

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
