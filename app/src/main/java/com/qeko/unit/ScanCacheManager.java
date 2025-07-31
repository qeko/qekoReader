package com.qeko.unit;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

public class ScanCacheManager {
    private static final String PREFS_NAME = "scan_cache";

    public static Set<String> getCachedDirs(Context context, String key) {
        return PreferenceManager.getDefaultSharedPreferences(context).getStringSet(key, new HashSet<>());
    }

    public static void saveCachedDirs(Context context, String key, Set<String> dirs) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putStringSet(key, dirs);
        editor.apply();
    }
}
