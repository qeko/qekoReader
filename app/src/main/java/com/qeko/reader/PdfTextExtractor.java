package com.qeko.reader;

import android.content.Context;
import android.util.Log;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PdfTextExtractor {

    // 从PDF文件路径提取全部文本行，返回字符串List
    public static List<String> extractTextLines(String filePath, Context context) {
        List<String> lines = new ArrayList<>();
        PDDocument document = null;
        try {
            File file = new File(filePath);
            document = PDDocument.load(file);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String fullText = stripper.getText(document);

            // 按换行符分割为行
            String[] splitLines = fullText.split("\\r?\\n");
            for (String line : splitLines) {
                if (!line.trim().isEmpty()) {
                    lines.add(line.trim());
                }
            }
        } catch (IOException e) {
            Log.e("PdfTextExtractor", "PDF解析失败", e);
        } finally {
            if (document != null) {
                try { document.close(); } catch (IOException ignored) {}
            }
        }
        return lines;
    }
}
