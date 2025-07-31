package com.qeko.reader;

import android.util.Log;

import java.io.File;

// ImageFileStrategy.java
public class ImageFileStrategy implements FileTypeStrategy {
    private static final String[] EXTENSIONS = {"jpg", "png", "gif"};

    @Override
    public boolean accept(File file) {
        String name = file.getName().toLowerCase();
        Log.d("ImageFileStrategy", "accept1: "+name);
        for (String ext : EXTENSIONS) {
            if (name.endsWith("." + ext)) {
                Log.d("ImageFileStrategy", "accept2: "+name);
                return true;
            }
        }
        return false;
    }
}
