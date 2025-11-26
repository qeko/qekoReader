package com.qeko.reader;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseFileScanStrategy implements FileScanStrategy {

    @Override
    public List<String> getFiles(File rootDir) {
        List<String> results = new ArrayList<>();
        scanDir(rootDir, results);
        return results;
    }

    private void scanDir(File dir, List<String> results) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDir(file, results);
            } else {
                if (matchFile(file)) {
                    results.add(file.getAbsolutePath());
                }
            }
        }
    }

    // 由子类实现的匹配逻辑
    protected abstract boolean matchFile(File file);
}