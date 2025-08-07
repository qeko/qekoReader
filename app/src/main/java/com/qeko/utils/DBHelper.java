package com.qeko.utils;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.qeko.reader.Book;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {


    private static DBHelper instance;

    public static synchronized DBHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DBHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DBHelper(Context context) {
        super(context, "books.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS Books (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT, " +
                "author TEXT, " +
                "format TEXT, " +
                "path TEXT, " +
                "wordCount INTEGER, " +
                "totalPages INTEGER, " +
                "currentPage INTEGER, " +
                "currentSentence INTEGER, " +
                "speechRate REAL, " +
                "categoryId INTEGER, " +
                "createTime LONG)");

        db.execSQL("CREATE TABLE IF NOT EXISTS Tags (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "bookId INTEGER, " +
                "pageIndex INTEGER, " +
                "name TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS Annotations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "bookId INTEGER, " +
                "pageIndex INTEGER, " +
                "content TEXT, " +
                "timestamp LONG)");

        db.execSQL("CREATE TABLE IF NOT EXISTS Comments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "bookId INTEGER, " +
                "content TEXT, " +
                "timestamp LONG)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public void insertIfNotExists(File file) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.query("Books", null, "path=?", new String[]{file.getAbsolutePath()}, null, null, null);
        if (c.moveToFirst()) {
            c.close(); return;
        }
        c.close();
        ContentValues cv = new ContentValues();
        cv.put("title", file.getName());
        cv.put("author", "未知");
        cv.put("format", getExtension(file));
        cv.put("path", file.getAbsolutePath());
        cv.put("wordCount", 0);
        cv.put("totalPages", 0);
        cv.put("currentPage", 0);
        cv.put("currentSentence", 0);
        cv.put("categoryId", 0);
        cv.put("createTime", System.currentTimeMillis());
        db.insert("Books", null, cv);
    }

    public List<Book> getBooksSorted(String sortBy) {
        List<Book> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query("Books", null, null, null, null, null, sortBy + " COLLATE NOCASE ASC");
        while (c.moveToNext()) {
            Book b = new Book();
            b.setId(c.getInt(c.getColumnIndex("id")));
            b.setTitle(c.getString(c.getColumnIndex("title")));
            b.setAuthor(c.getString(c.getColumnIndex("author")));
            b.setFormat(c.getString(c.getColumnIndex("format")));
            b.setPath(c.getString(c.getColumnIndex("path")));
            b.setWordCount(c.getInt(c.getColumnIndex("wordCount")));
            b.setTotalPages(c.getInt(c.getColumnIndex("totalPages")));
            b.setCurrentPage(c.getInt(c.getColumnIndex("currentPage")));
            b.setCurrentSentence(c.getInt(c.getColumnIndex("currentSentence")));

            b.setCategoryId(c.getInt(c.getColumnIndex("categoryId")));
            b.setCreateTime(c.getLong(c.getColumnIndex("createTime")));
            list.add(b);
        }
        c.close();
        return list;
    }



    private String getExtension(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf(".");
        return dot > 0 ? name.substring(dot + 1).toLowerCase() : "";
    }


    public static float getSpeechRate(Context context, int bookId) {
        float rate = 1.0f;
        SQLiteDatabase db = new DBHelper(context).getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT speechRate FROM Books WHERE id=?", new String[]{String.valueOf(bookId)});
        if (cursor.moveToFirst()) rate = cursor.getFloat(0);
        cursor.close();
        return rate;
    }

/*    public static void saveSpeechRate( int bookId, float speechRate) {
        SQLiteDatabase db = new DBHelper(context).getWritableDatabase();
        db.execSQL("UPDATE Books SET speechRate=? WHERE id=?", new Object[]{speechRate, bookId});
    }*/

    public void updateCurrentPageInDB(Context context, int currentSentence, int currentPage, float speechRate, int bookId) {
        SQLiteDatabase db = DBHelper.getInstance(context).getWritableDatabase();
        db.execSQL("UPDATE Books SET currentSentence=?, currentPage=?, speechRate=? WHERE id=?",
                new Object[]{currentSentence, currentPage, speechRate, bookId});
    }


/*    private void saveReadingProgress() {

        db.getWritableDatabase().execSQL("UPDATE Books SET currentPage=?, currentSentence=? WHERE id=?",
                new Object[]{currentPage, savedLineIndex, bookId});
    }*/

    }