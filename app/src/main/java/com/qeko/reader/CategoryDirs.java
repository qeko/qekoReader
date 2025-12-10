package com.qeko.reader;


import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CategoryDirs {
    private Map<String, List<File>> categoryMap = new HashMap<>();

    public void add(String key, File dir) {
        if (!categoryMap.containsKey(key)) {
            categoryMap.put(key, new ArrayList<>());
        }
        List<File> dirs = categoryMap.get(key);
        if (!dirs.contains(dir)) {
            dirs.add(dir);
        }
    }

    public List<File> getDirs(String key) {
        List<File> dirs = categoryMap.get(key);
        return dirs != null ? dirs : new ArrayList<>();
    }

    public Map<String, List<File>> getAll() {
        return categoryMap;
    }

    public static CategoryDirs fromMap(Map<String, List<String>> map) {
        CategoryDirs dirs = new CategoryDirs();
        for (String k : map.keySet()) {
            List<String> paths = map.get(k);
            if (paths == null) continue;
            for (String p : paths) dirs.add(k, new File(p));
        }
        return dirs;
    }
}
