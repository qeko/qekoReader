

package com.qeko.utils;

/*import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;*/


import static android.content.ContentValues.TAG;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
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
//import com.bumptech.glide.load.engine.Resource;
import  nl.siegmann.epublib.domain.Resource;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.LocationTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

import com.qeko.reader.FileTypeStrategy;
import com.qeko.reader.MainActivity;
import com.qeko.reader.PageOffset;


import java.io.File;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.util.Log;

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


    private static   ExecutorService executorService ;

    public static  void processPdfListInBackground(final ArrayList<File> fileList, final Context context) {
        new Thread(() -> {
            for (File file : fileList) {
                try {
                    String nameLower = file.getName().toLowerCase();
                    if (nameLower.endsWith(".pdf")) {
                        // 生成临时文件路径
                        String pdfPath = file.getAbsolutePath();
                        String tempPath = pdfPath + ".pdftemp";
                        String txtPath = pdfPath + ".pdftxt";

                        File tempFile = new File(tempPath);
                        File txtFile = new File(txtPath);

                        // 如果临时文件存在则跳过，避免重复处理
                        if (!txtFile.exists()) {
                            // 删除旧的临时文件（保证重新生成）
                            if (tempFile.exists()) tempFile.delete();
//                            extractTextFromPdf(file, context);
                            extractTextFromPdf(file, context, "fonts/SimsunExtG.ttf");
                        }
                    } else if (nameLower.endsWith(".epub")) {
                        // 生成临时文件路径
                        String epubPath = file.getAbsolutePath();
                        String tempPath = epubPath + ".epubtemp";
                        String txtPath = epubPath + ".epubtxt";

                        File tempFile = new File(tempPath);
                        File txtFile = new File(txtPath);

                        // 如果临时文件存在则跳过，避免重复处理
                        if (!txtFile.exists()) {
                            // 删除旧的临时文件（保证重新生成）
                            if (tempFile.exists()) tempFile.delete();
//                            extractTextFromEpubByBatch(file, context);
                            extractTextFromEpubByBatch( context,file,txtFile);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();



    }

/*
    public static void processPdfListInBackground(ArrayList<File> pdfFiles, Context context) {
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newFixedThreadPool(2);
        }

        executorService.execute(() -> {
            for (File pdf : pdfFiles) {
                try {
                    // 提取文本
                    String text = FileUtils.extractTextFromPdf(pdf, context, "fonts/SimsunExtG.ttf");

                    if (text != null && !text.trim().isEmpty()) {
                        // 写入 .pdftemp 文件
                        File tempFile = new File(pdf.getParent(), pdf.getName() + ".pdftemp");
                        try (FileWriter writer = new FileWriter(tempFile)) {
                            writer.write(text);
                        }

                        // 重命名为 .pdftxt（先删除已有的 .pdftxt）
                        File txtFile = new File(pdf.getParent(), pdf.getName() + ".pdftxt");
                        if (txtFile.exists()) {
                            txtFile.delete();
                        }
                        tempFile.renameTo(txtFile);
                    } else {
                        Log.e("PDF", "提取文本失败，下次会重新生成: " + pdf.getName());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
*/

    public static String extractTextFromPdf(File file, Context context, String fontAssetPath) {
        StringBuilder text = new StringBuilder();
        PdfReader reader = null;
        try {
            File fontFile = copyFontFromAssets(context, fontAssetPath); // 可选
            reader = new PdfReader(file.getAbsolutePath());
            int numPages = reader.getNumberOfPages();

            for (int i = 1; i <= numPages; i++) {
                String pageText = PdfTextExtractor.getTextFromPage(reader, i);
                text.append(pageText).append("\n");
            }

            Log.d("PDF", file.getAbsolutePath()+"PDF 文本提取完成，共 " + numPages + " 页");
        } catch (Exception e) {
            Log.e("PDF", "extractTextFromPdf 出错", e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return text.toString();
    }
    /*
    public static void extractTextFromPdf(File pdfFile, Context context) {
        String pdfPath = pdfFile.getAbsolutePath();
        String tempPath = pdfPath + ".pdftemp";
        String txtPath = pdfPath + ".pdftxt";

        File tempFile = new File(tempPath);
        File txtFile = new File(txtPath);

        // 如果txt已经存在，直接跳过
        if (txtFile.exists()) {
            return;
        }

        // 每次重新生成 pdftemp 覆盖旧的
        if (tempFile.exists()) {
            tempFile.delete();
        }

        boolean success = false;
        try {
            // TODO: 实现 PDF → 文本的提取逻辑
            // 示例：假设 extractPdfTextToFile() 会把解析结果写入 tempFile
//            success = extractPdfTextToFile(pdfFile, tempFile);
            extractTextFromPdf(pdfFile, context, "fonts/SimsunExtG.ttf");
        } catch (Exception e) {
            e.printStackTrace();
            success = false;
        }

        if (success && tempFile.exists()) {
            // 重命名为 .pdftxt
            if (txtFile.exists()) txtFile.delete();
            tempFile.renameTo(txtFile);
        }
        // 如果失败，下次重新生成（保留/覆盖 .pdftemp 文件）
    }
*/
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



/*    private static boolean extractPdfTextToFile(File pdfFile, File outFile) {
        // 示例：这里实现 PDF 文本解析
        try (FileWriter writer = new FileWriter(outFile)) {
            // 仅测试：写入固定字符串（你可以换成真实解析）
            writer.write("Extracted text from: " + pdfFile.getName());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }*/




    /**
     * 从PDF提取文字并按100页批次追加写入 .pdftxt 文件

     * @return 生成的 .pdftxt 文件路径
     */
/*
    public static boolean extractTextFromPdf(File pdfFile, Context context, File outputTxtFile) {
        String pdfPath = pdfFile.getAbsolutePath();
//        String outputPath = pdfPath + ".pdftxt";
//        File outFile = new File(outputPath);

        if (outputTxtFile.exists()) {
            outputTxtFile.delete(); // 确保重新生成
        }

        FileOutputStream fos = null;
        PdfReader reader = null;

        try {
            reader = new PdfReader(pdfPath);
            int totalPages = reader.getNumberOfPages();
            fos = new FileOutputStream(outputTxtFile, true); // 追加模式

            StringBuilder batchText = new StringBuilder();
            int batchSize = 100;

            for (int page = 1; page <= totalPages; page++) {
                String text = PdfTextExtractor.getTextFromPage(reader, page);
                batchText.append(text).append("\n");

                // 每满100页 或最后一页，写入一次
                if (page % batchSize == 0 || page == totalPages) {
                    fos.write(batchText.toString().getBytes("UTF-8"));
                    fos.flush();
                    Log.d("FileUtils", "已写入页码: " + (page - batchSize + 1) + "-" + page);
                    batchText.setLength(0); // 清空 StringBuilder
                }
            }

//            Log.d("FileUtils", "提取完成，生成: " );
//            return outputPath;

        } catch (Exception e) {
            e.printStackTrace();
//            return null;

        } finally {
            if (reader != null) reader.close();
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) {}
            }
        }
        return  true;
    }
*/

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

    /**
     * 按页读取文本文件（TXT/EPUB等）
     * @param file 待读取文件
     * @param pageNum 页码（0开始）
     * @param charsPerPage 每页字符数（近似）
     * @return 该页的文本
     */
    public static String readFilePage(File file, int pageNum, int charsPerPage) {
        Charset charset = detectEncoding(file);
        int startOffset = pageNum * charsPerPage;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            if (startOffset >= fileLength) return ""; // 超过文件末尾

            raf.seek(startOffset);

            int bytesToRead = (int) Math.min(charsPerPage, fileLength - startOffset);
            byte[] buffer = new byte[bytesToRead];
            int readBytes = raf.read(buffer);

            if (readBytes <= 0) return "";

            return new String(buffer, 0, readBytes, charset);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 按 100 页批量提取 EPUB 文本，写入目标 .epubtxt 文件
     * @param context 上下文
     * @param epubFile EPUB 文件
     * @param outputTxtFile 输出的文本文件
     */
    public static void extractTextFromEpubByBatch(Context context, File epubFile, File outputTxtFile) {
        try {

            Log.d(TAG, "extractTextFromEpubByBatch: " +outputTxtFile.getName());
            Book book = new EpubReader().readEpub(new FileInputStream(epubFile));
            Spine spine = book.getSpine();
            List<SpineReference> spineRefs = spine.getSpineReferences();
            int totalChapters = spineRefs.size();

            int batchSize = 100; // 每批处理 100 个章节
            int chapterIndex = 0;

            // 如果输出文件存在，先清空
            if (outputTxtFile.exists()) outputTxtFile.delete();

            while (chapterIndex < totalChapters) {
                int endIndex = Math.min(chapterIndex + batchSize, totalChapters);
                StringBuilder sb = new StringBuilder();

                for (int i = chapterIndex; i < endIndex; i++) {
                    SpineReference ref = spineRefs.get(i);
                    Resource res = ref.getResource();
                    if (res != null) {
                        try {
                            String html = new String(res.getData(), StandardCharsets.UTF_8);
                            // 去除 HTML 标签，只保留文本
                            String text = html.replaceAll("<[^>]+>", "\n").replaceAll("\\s+", " ").trim();
                            sb.append(text).append("\n");
                        } catch (Exception e) {
                            Log.e(TAG, "解析章节失败: " + i, e);
                        }
                    }
                }

                // 追加写入文件
                try (FileOutputStream fos = new FileOutputStream(outputTxtFile, true);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    writer.write(sb.toString());
                    writer.flush();
                }

                Log.d(TAG, "已处理章节: " + chapterIndex + " ~ " + (endIndex - 1));
                chapterIndex = endIndex;
            }

            Log.d(TAG, "EPUB 文本提取完成，保存到：" + outputTxtFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "extractTextFromEpubByBatch 出错", e);
        }
    }


    // 序列化保存分页偏移数组

    public static void savePageOffsets(Context ctx, String filePath, List<Integer> offsets) {
        Log.d(TAG, "savePageOffsets: 11");
        if (offsets == null) return;
        Log.d(TAG, "savePageOffsets: 22");
        File cacheFile = new File(ctx.getCacheDir(), new File(filePath).getName() + ".pagecache");
        Log.d(TAG, "savePageOffsets: 33");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cacheFile))) {
            Log.d(TAG, "savePageOffsets: 44");
            oos.writeObject(offsets);
            Log.d(TAG, "savePageOffsets: 55");
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "savePageOffsets: OK");
    }


    // 反序列化读取分页偏移数组
    @SuppressWarnings("unchecked")
    public static List<Integer> loadPageOffsets(Context ctx, String filePath) {
        File cacheFile = new File(ctx.getCacheDir(), new File(filePath).getName() + ".pagecache");
        if (!cacheFile.exists()) return new ArrayList<>();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cacheFile))) {
            return (List<Integer>) ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // 获取缓存文件（按PDF路径生成唯一文件名）
    private static File getPageOffsetCacheFile(Context context, String filePath) {
        String safeName = filePath.replaceAll("[^a-zA-Z0-9.-]", "_");
        return new File(context.getCacheDir(), safeName + ".pageOffsets");
    }

    // 删除缓存（如果需要）
    public static void deletePageOffsetCache(Context context, String filePath) {
        File f = getPageOffsetCacheFile(context, filePath);
        if (f.exists()) f.delete();
    }
/*
    // 从磁盘加载已序列化的 PageOffsets
    public static List<PageOffset> loadPageOffsets(Context context, String filePath) {
        try {
            File f = new File(context.getFilesDir(), new File(filePath).getName() + PAGE_OFFSET_EXT);
            if (!f.exists()) return null;
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
            List<PageOffset> list = (List<PageOffset>) ois.readObject();
            ois.close();
            return list;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 保存 PageOffsets 到磁盘
    public static void savePageOffsets(Context context, String filePath, List<PageOffset> list) {
        try {
            File f = new File(context.getFilesDir(), new File(filePath).getName() + PAGE_OFFSET_EXT);
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
            oos.writeObject(list);
            oos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/

}
