
package com.qeko.reader;

import android.util.Log;

import com.qeko.utils.FileUtils;

import java.io.File;
import java.util.Locale;

// BookFileStrategy.java
public class RecycleBinStrategy implements FileTypeStrategy {

    private static final String[] EXTENSIONS = {
           ".待删除"
    };

    @Override
    public boolean accept(File file) {
        if (file == null || !file.isFile()) return false;
        return acceptName(file.getName());
    }

    public static boolean acceptName(String name) {
        if (name == null) return false;

        name = name.toLowerCase(Locale.US);
        for (String ext : EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
