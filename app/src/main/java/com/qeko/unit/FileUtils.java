

package com.qeko.unit;


import android.util.Log;

import java.io.File;

import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    // 递归扫描 .txt 文件
    private static final String[] EXT = {"txt","pdf","epub","mobi","azw","azw3"};

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


    // 格式化文件大小
    public static String readableFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = {"B","KB","MB","GB","TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new java.text.DecimalFormat("#,##0.#")
                .format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String getExtension(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        return i>0 ? n.substring(i+1).toLowerCase() : "";
    }
}
