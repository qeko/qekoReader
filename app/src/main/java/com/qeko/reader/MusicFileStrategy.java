package com.qeko.reader;

import android.util.Log;

import java.io.File;

// MusicFileStrategy.java
public class MusicFileStrategy implements FileTypeStrategy {
    private static final String[] EXTENSIONS = {"mp3", "wav", "flac"};

    @Override
    public boolean accept(File file) {
        String name = file.getName().toLowerCase();
        Log.d("MusicFileStrategy", "accept1: "+name);
        for (String ext : EXTENSIONS) {
            if (name.endsWith("." + ext)) {
                Log.d("MusicFileStrategy", "accept2: "+name);
                return true;
            }
        }
        return false;
    }
}
