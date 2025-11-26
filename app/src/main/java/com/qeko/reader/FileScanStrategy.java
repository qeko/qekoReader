package com.qeko.reader;

import java.io.File;
import java.util.List;

public interface FileScanStrategy {
    List<String> getFiles(File rootDir);
}