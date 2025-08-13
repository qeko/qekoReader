

package com.qeko.utils;

/*import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;*/


import static android.content.ContentValues.TAG;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.text.TextUtils;

//import androidx.pdf.PdfDocument;
import androidx.preference.PreferenceManager;

/*import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;*/


//import com.itextpdf.text.pdf.parser.SimpleTextExtractionStrategy;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.LocationTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import com.qeko.reader.FileTypeStrategy;
import com.qeko.reader.PdfReaderActivity;
/*import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentCatalog;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.PDPageTree;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font;
import com.tom_roush.pdfbox.text.PDFTextStripper;*/

import java.io.File;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.util.Log;

import org.mozilla.universalchardet.UniversalDetector;

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

    /**
     * 从 PDF 文件提取所有文本（支持中文字体）
     *
     * @param file         PDF 文件
     * @param context      Android Context
     * @param fontAssetPath assets 中字体文件路径，例如 "fonts/SimsunExtG.ttf"
     * @return 提取到的文本
     */
  /*  public static String extractTextFromPdf(File file, Context context, String fontAssetPath) {
        StringBuilder text = new StringBuilder();

        PdfDocument pdfDoc = null;
        try {
            // 仍然保留字体复制逻辑，接口一致（虽然iText7不会直接用它）
            File fontFile = copyFontFromAssets(context, fontAssetPath);

            // 打开 PDF
            pdfDoc = new PdfDocument(new PdfReader(file));

            int numPages = pdfDoc.getNumberOfPages();
            // 测试时只读取前2页，可改成 numPages
            numPages = Math.min(numPages, 2);

            for (int i = 1; i <= numPages; i++) {
                // 使用 LocationTextExtractionStrategy 保持文字顺序
                String pageText = PdfTextExtractor.getTextFromPage(
                        pdfDoc.getPage(i),
                        new LocationTextExtractionStrategy()
                );
                text.append(pageText).append("\n");
            }

            Log.d(TAG, "PDF 文本提取完成，共 " + numPages + " 页");
        } catch (Exception e) {
            Log.e(TAG, "extractTextFromPdf 出错", e);
        } finally {
            if (pdfDoc != null) {
                pdfDoc.close();
            }
        }
//        Log.d("DEBUG", "extractTextFromPdf=" + Arrays.toString(text.toString().getBytes()));
        return text.toString();
    }*/
/*        StringBuilder text = new StringBuilder();
        try {
            PdfDocument pdfDoc = new PdfDocument(new PdfReader(file));
            int numPages = pdfDoc.getNumberOfPages();
            for (int i = 1; i <= numPages; i++) {
                String pageText = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i));
                text.append(pageText).append("\n");
            }
            pdfDoc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return text.toString();*/


    public static String extractTextFromPdf(File file, Context context, String fontAssetPath) {
             StringBuilder text = new StringBuilder();

        PdfReader reader = null;
        try {
            // 确保字体文件从 assets 拷贝到临时路径（即使 iText 不直接用它，也方便后续扩展）

            File fontFile = copyFontFromAssets(context, fontAssetPath);

            // 打开 PDF
            reader = new PdfReader(file.getAbsolutePath());

            int numPages = reader.getNumberOfPages();
            numPages = 2;
            for (int i = 1; i <= numPages; i++) {
                String pageText = PdfTextExtractor.getTextFromPage(reader, i);
//                String rawText = PdfTextExtractor.getTextFromPage(reader, i, new LocationTextExtractionStrategy());

//                text.append(pageText).append("\n");

                // 强制转为 UTF-8 避免乱码
//                String utf8Text = new String(rawText.getBytes("ISO-8859-1"), "UTF-8");

                text.append(pageText).append("\n");

            }

            Log.d(TAG, "PDF 文本提取完成，共 " + numPages + " 页");
        } catch (Exception e) {
            Log.e(TAG, "extractTextFromPdf 出错", e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return text.toString();
    }



    private static Charset detectEncoding(File file) {
        byte[] buf = new byte[4096];
        try (FileInputStream fis = new FileInputStream(file)) {
            UniversalDetector detector = new UniversalDetector(null);
            int nread;
            while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, nread);
            }
            detector.dataEnd();
            String encoding = detector.getDetectedCharset();
            detector.reset();
            if (encoding != null) {
                return Charset.forName(encoding);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Charset.forName("GBK");
    }

    /**
     * 从 assets 中拷贝字体文件到 cache 目录
     */
    private static File copyFontFromAssets(Context context, String assetPath) throws Exception {
        AssetManager am = context.getAssets();
        InputStream is = am.open(assetPath);
        File outFile = new File(context.getCacheDir(), new File(assetPath).getName());
        FileOutputStream fos = new FileOutputStream(outFile);

        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            fos.write(buffer, 0, length);
        }
        fos.flush();
        fos.close();
        is.close();

        return outFile;
    }
}
