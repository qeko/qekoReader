package com.qeko.reader;

import java.io.File;



public interface FileTypeStrategy {
    boolean accept(File file);
}