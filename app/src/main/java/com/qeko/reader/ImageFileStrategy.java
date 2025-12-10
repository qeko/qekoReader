package com.qeko.reader;

import android.util.Log;

import java.io.File;

// ImageFileStrategy.java
public class ImageFileStrategy implements FileTypeStrategy {
    private static final String[] EXTENSIONS = {".jpg", ".png", ".bmp", ".jpeg", ".gif"};

    public static boolean acceptName(String name){
        if (name==null) return false;
        for (String e:EXTENSIONS) if (name.endsWith(e)) return true;
        return false;
    }
    @Override public boolean accept(File f){ return acceptName(f.getName().toLowerCase()); }
}