package com.qeko.unit;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class DocumentTracker {
    private static final String PREF = "reader_prefs";
    private static final String KEY_LAST = "last_file_path";

    public static void setLastRead(Context ctx, String path) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST, path).apply();
    }

    public static String getLastRead(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY_LAST, "");
    }
}
