

package com.qeko.utils;

import java.io.File;
import java.io.IOException;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.qeko.reader.FileTypeStrategy;
import com.qeko.reader.PdfReaderActivity;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentCatalog;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.PDPageTree;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.File;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.util.Log;

public class FileUtils {
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

    /**
     * 从PDF文件中提取指定页码的文本内容。
     *
     * @param file      PDF文件
     * @param context   Android上下文（暂时未用，可用于字体加载等扩展）
     * @param fontName  字体名（预留参数，可忽略）
     * @param pageIndex 从0开始的页码
     * @return 提取到的该页文本，失败则返回空字符串
     */
    public static String extractTextFromPdfPage(File file, Context context, String fontName, int pageIndex) {
        String result = "";
        PDDocument document = null;

        try {
            document = PDDocument.load(file);

            // 页码从1开始，所以 +1
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);

            result = stripper.getText(document).trim();

        } catch (Exception e) {
            Log.e("FileUtils", "extractTextFromPdfPage failed: " + e.getMessage());
        } finally {
            try {
                if (document != null) document.close();
            } catch (Exception e) {
                Log.e("FileUtils", "Failed to close document: " + e.getMessage());
            }
        }

        return result;
    }

    public static String extractTextFromPdf(File file, Context context, String fontAssetName) {
        try {
            PDFBoxResourceLoader.init(context);
            PDDocument document = PDDocument.load(file);

            // 解决中文字体显示
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            PDPageTree pages = catalog.getPages();
            PDPageContentStream contentStream;

            // 加载字体文件
            InputStream fontStream = context.getAssets().open(fontAssetName);
            PDFont font = PDType0Font.load(document, fontStream, true);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            String text = stripper.getText(document);
            document.close();

            return text;

        } catch (IOException e) {
            e.printStackTrace();
            return "PDF读取失败：" + e.getMessage();
        }
    }

//    private static final String[] EXT = {"txt","pdf","epub","mobi","azw","azw3"};
/*

    public  static  List<File> scanAll(File dir) {

        List<File> out = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) return out;
        File[] fs = dir.listFiles();
        if (fs == null) return out;
        for (File f : fs) {
            if (f.isDirectory()) {
                out.addAll(scanAll(f));
            } else
                for (String s: EXT) {
                    String e = getExtension(f);
                    if (s.equalsIgnoreCase(e)) {
                        out.add(f);
                        Log.d("Scanner", "Found: "+f.getAbsolutePath());
                        break;
                    }
                }
//                if (f.getName().toLowerCase().endsWith(".txt")) {
//                out.add(f);
        }
        return out;
    }
*/

    public static List<File> scanFilesIn(File dir, FileTypeStrategy strategy) {
        List<File> result = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && strategy.accept(file)) {
                    result.add(file);
                }
            }
        }
        return result;
    }


    private static final String PREFS_NAME = "scan_cache";


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


/*
    public static int countMatchingFiles(File dir, FileTypeStrategy strategy) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            Log.d("TAG", "Scanning directory: " + dir.getAbsolutePath());
            for (File file : files) {

                Log.d("file.isFile()", file.isFile()+"===Found file===: " + file.getName());
                if (file.isFile() && strategy.accept(file)) {
                    count++;
                    Log.d("TAG", count+"Found file: " + file.getName());
                }
            }
        }
        Log.d("TAG", "countMatchingFiles: "+count);
        return count;
    }*/



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

    public static String getExtension(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        return i>0 ? n.substring(i+1).toLowerCase() : "";
    }

    public static void saveScanResults(Context context, Map<String, List<File>> categoryDirs) {
        SharedPreferences prefs = context.getSharedPreferences("scan_cache", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        for (Map.Entry<String, List<File>> entry : categoryDirs.entrySet()) {
            String key = entry.getKey();
            List<String> paths = new ArrayList<>();
            for (File file : entry.getValue()) {
                if (file.isDirectory()) {
                    paths.add(file.getAbsolutePath());
                }
            }
            Log.d("TAG", key+"saveScanResults: "+paths);
            editor.putString(key, TextUtils.join(";", paths));
        }

        editor.apply();
    }


    private static final int MAX_RECENT_BOOKS = 5;

    public static void saveRecentBook(Context context, File file, int pageCount, int charsPerPage) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();

        String recent = prefs.getString("recent_books", "");
        List<String> list = new LinkedList<>();
        if (!recent.isEmpty()) {
            String[] entries = recent.split("\\|\\|");
            for (String entry : entries) {
                if (!entry.startsWith(file.getAbsolutePath())) {
                    list.add(entry);
                }
            }
        }

        String newEntry = file.getAbsolutePath() + "," + pageCount + "," + charsPerPage;
        list.add(0, newEntry);

        while (list.size() > MAX_RECENT_BOOKS) {
            list.remove(list.size() - 1);
        }

        editor.putString("recent_books", String.join("||", list));
        editor.apply();
    }

    public static List<FileItem> getRecentBooks(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String recent = prefs.getString("recent_books", "");
        List<FileItem> result = new ArrayList<>();

        if (!recent.isEmpty()) {
            String[] entries = recent.split("\\|");
            for (String entry : entries) {
                String[] parts = entry.split(",");
                if (parts.length == 3) {
                    File file = new File(parts[0]);
                    if (file.exists()) {
                        int pages = Integer.parseInt(parts[1]);
                        int charsPerPage = Integer.parseInt(parts[2]);
                        FileItem item = new FileItem(file, false);
                        item.setPageCount(pages);
                        item.setCharsPerPage(charsPerPage);
                        item.setPinned(true);
                        item.setLastRead(true);
                        result.add(item);
                    }
                }
            }
        }

        return result;
    }

/*    public static String extractTextFromPdf(File file, PdfReaderActivity pdfReaderActivity, String s) {
    }*/
}
