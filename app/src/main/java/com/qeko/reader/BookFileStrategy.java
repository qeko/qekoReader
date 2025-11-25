package com.qeko.reader;

import android.util.Log;

import com.qeko.utils.FileUtils;

import java.io.File;

// BookFileStrategy.java
public class BookFileStrategy implements FileTypeStrategy {
    private static final String[] EXTENSIONS = {"txt", "pdf", "epub", "mobi", "azw", "azw3"};

    @Override
    public boolean accept(File file) {
        String name = file.getName().toLowerCase();
        Log.d("BookFileStrategy", "accept1: "+name);
        for (String ext : EXTENSIONS) {

            if (name.endsWith("." + ext)) {
//                Log.d("BookFileStrategy", "accept2: "+name);
                return true;
            }
        }
        return false;
    }



}
