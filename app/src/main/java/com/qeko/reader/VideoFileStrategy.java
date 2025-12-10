package com.qeko.reader;

import java.io.File;

public class VideoFileStrategy implements FileTypeStrategy {
    private static final String[] EXT = {".mp4",".mkv",".avi",".mov",".wmv",".ts"};
    public static boolean acceptName(String name){
        if (name==null) return false;
        for (String e:EXT) if (name.endsWith(e)) return true;
        return false;
    }
    @Override public boolean accept(File f){ return acceptName(f.getName().toLowerCase()); }
}
