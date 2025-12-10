package com.qeko.reader;

import android.util.Log;

import com.qeko.utils.FileUtils;

import java.io.File;

// BookFileStrategy.java
public class BookFileStrategy implements FileTypeStrategy {
    private static final String[] EXTENSIONS = {".txt", ".pdf", ".epub", ".mobi", ".azw", ".azw3"};

        public static boolean acceptName(String name) {
            name = name.toLowerCase();
            for (String ext : EXTENSIONS) if (name.endsWith(ext)) return true;
            return false;
        }

        @Override
        public boolean accept(File file) {
            if (file == null || !file.exists() || !file.isFile()) return false;
            String name = file.getName().toLowerCase();
            return name.endsWith(".txt") ;
        }

    }