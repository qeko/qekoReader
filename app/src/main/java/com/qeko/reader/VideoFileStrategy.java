package com.qeko.reader;

import android.util.Log;

import java.io.File;

// VideoFileStrategy.java
public class VideoFileStrategy implements FileTypeStrategy {
    private static final String[] EXTENSIONS = {"mp4", "mkv", "avi", "mpg"};

    @Override
    public boolean accept(File file) {
        String name = file.getName().toLowerCase();
        Log.d("VideoFileStrategy", "accept1: "+name);
        for (String ext : EXTENSIONS) {
            if (name.endsWith("." + ext)) {
                Log.d("VideoFileStrategy", "accept2: "+name);
                return true;
            }
        }
        return false;
    }
}
