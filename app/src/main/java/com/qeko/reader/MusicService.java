 package com.qeko.reader;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

 public class MusicService extends Service {
     private MediaPlayer mediaPlayer;
     private List<File> musicFiles = new ArrayList<>();
     private int currentIndex = 0;


     public interface OnTrackChangeListener {
         void onTrackChanged(File newTrack);
     }

     private OnTrackChangeListener trackChangeListener;

     public void setOnTrackChangeListener(OnTrackChangeListener listener) {
         this.trackChangeListener = listener;
     }

     @Override
     public IBinder onBind(Intent intent) {
         return new MusicBinder();
     }

     public class MusicBinder extends Binder {
         public MusicService getService() {
             return MusicService.this;
         }
     }
     private RepeatMode repeatMode = RepeatMode.SEQUENTIAL;
     public enum RepeatMode {
         SEQUENTIAL, SHUFFLE, REPEAT_ONE
     }

     public void setMusicFromFilePath(String filePath) {
         musicFiles.clear();
         currentIndex = 0;

         if (filePath != null) {
             File currentMusic = new File(filePath);
             File parentDir = currentMusic.getParentFile();
             if (parentDir != null && parentDir.isDirectory()) {
                 File[] files = parentDir.listFiles();
                 if (files != null) {
                     for (File f : files) {
                         if (f.isFile() && isMusicFile(f.getName())) {
                             musicFiles.add(f);
                         }
                     }
                     currentIndex = musicFiles.indexOf(currentMusic);
                     if (currentIndex < 0) currentIndex = 0;
                 }
             }
         }
         playCurrent();
     }

     private boolean isMusicFile(String name) {
         String lower = name.toLowerCase();
         return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") || lower.endsWith(".flac");
     }

     private void playCurrent() {
         if (musicFiles.isEmpty()) return;

         File file = musicFiles.get(currentIndex);

         if (mediaPlayer != null) {
             mediaPlayer.release();
         }

         mediaPlayer = new MediaPlayer();
         try {
             mediaPlayer.setDataSource(file.getAbsolutePath());
             mediaPlayer.prepare();
             mediaPlayer.start();
         } catch (IOException e) {
             e.printStackTrace();
         }

         // 通知 Activity
         if (trackChangeListener != null) {
             trackChangeListener.onTrackChanged(file);
         }

         mediaPlayer.setOnCompletionListener(mp -> playNext());
     }

/*
     private void playCurrent() {
         if (musicFiles.isEmpty()) return;

         File file = musicFiles.get(currentIndex);

         if (mediaPlayer != null) {
             mediaPlayer.release();
         }

         mediaPlayer = new MediaPlayer();
         try {
             mediaPlayer.setDataSource(file.getAbsolutePath());
             mediaPlayer.prepare();
             mediaPlayer.start();
         } catch (IOException e) {
             e.printStackTrace();
         }

         mediaPlayer.setOnCompletionListener(mp -> playNext());
     }
*/

     public void play(Uri uri) {

         try {
             mediaPlayer.reset();
             mediaPlayer.setDataSource(getApplicationContext(), uri);
             mediaPlayer.prepare();
             mediaPlayer.start();

         } catch (IOException e) {

         }
     }

     public boolean isPlaying() {
         return mediaPlayer.isPlaying();
     }
     public void pause() {
         if (mediaPlayer.isPlaying()) {
             mediaPlayer.pause();

         }
     }




     public void seekTo(int msec) {
         mediaPlayer.seekTo(msec);
     }

     public int getCurrentPosition() {
         return mediaPlayer.getCurrentPosition();
     }

     public int getDuration() {
         return mediaPlayer.getDuration();
     }

     public void playNext() {
         if (musicFiles.isEmpty()) return;
         currentIndex = (currentIndex + 1) % musicFiles.size(); // 循环播放
         playCurrent();
     }

     public void setRepeatMode(String modeName) {
         try {
             repeatMode = RepeatMode.valueOf(modeName);

         } catch (IllegalArgumentException e) {

         }
     }

     public void stop() {
         if (mediaPlayer != null) {
             mediaPlayer.stop();
             mediaPlayer.release();
             mediaPlayer = null;
         }
     }

     @Override
     public void onDestroy() {
         super.onDestroy();
         stop();
     }
 }
