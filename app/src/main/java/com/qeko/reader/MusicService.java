 package com.qeko.reader;


 import android.app.Notification;
 import android.app.NotificationChannel;
 import android.app.NotificationManager;
 import android.app.Service;
 import android.content.Intent;
 import android.media.MediaPlayer;
 import android.net.Uri;
 import android.os.Binder;
 import android.os.Build;
 import android.os.IBinder;
 import android.util.Log;

 import androidx.annotation.Nullable;
 import androidx.core.app.NotificationCompat;

 import java.io.File;
 import java.io.IOException;
 import java.util.ArrayList;
 import java.util.List;
 import java.util.Random;

 public class MusicService extends Service {

     private static final String TAG = "MusicService";
     private static final String CHANNEL_ID = "MusicPlayerChannel";

     private MediaPlayer mediaPlayer;
     private Uri currentMusicUri;
     private final IBinder binder = new MusicBinder();

     private List<File> playList = new ArrayList<>();
     private int currentIndex = 0;

     // 三种播放模式
     private String repeatMode = "SEQUENTIAL"; // 可取值：SEQUENTIAL / SHUFFLE / REPEAT_ONE

     // 外部监听接口
     public interface OnTrackChangeListener {
         void onTrackChange(File newTrack);
     }
     private OnTrackChangeListener trackChangeListener;

     public void setOnTrackChangeListener(OnTrackChangeListener listener) {
         this.trackChangeListener = listener;
     }

     @Nullable
     @Override
     public IBinder onBind(Intent intent) {
         return binder;
     }

     @Override
     public void onCreate() {
         super.onCreate();
         createNotificationChannel();
         Log.d(TAG, "MusicService created");
     }

     @Override
     public int onStartCommand(Intent intent, int flags, int startId) {
         startForeground(1, buildNotification("音乐播放中..."));
         return START_STICKY;
     }

     private Notification buildNotification(String text) {
         return new NotificationCompat.Builder(this, CHANNEL_ID)
                 .setContentTitle("Music Player")
                 .setContentText(text)
                 .setSmallIcon(android.R.drawable.ic_media_play)
                 .setOngoing(true)
                 .build();
     }

     private void createNotificationChannel() {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             NotificationChannel channel = new NotificationChannel(
                     CHANNEL_ID,
                     "Music Playback",
                     NotificationManager.IMPORTANCE_LOW
             );
             NotificationManager manager = getSystemService(NotificationManager.class);
             if (manager != null) manager.createNotificationChannel(channel);
         }
     }

     public class MusicBinder extends Binder {
         public MusicService getService() {
             return MusicService.this;
         }
     }

     // 设置播放列表（可选）
     public void setPlaylist(List<File> list) {
         this.playList = list;
     }

     public void setMusicFromFilePath(String filePath) {
         File file = new File(filePath);
         currentMusicUri = Uri.fromFile(file);
         if (!playList.contains(file)) {
             playList.add(file);
             currentIndex = playList.indexOf(file);
         }
         play(currentMusicUri);
     }

     public void setRepeatMode(String mode) {
         this.repeatMode = mode;
         Log.d(TAG, "Repeat mode set to: " + mode);
     }

     public boolean isPlaying() {
         return mediaPlayer != null && mediaPlayer.isPlaying();
     }

     public boolean hasTrackLoaded() {
         return mediaPlayer != null && currentMusicUri != null;
     }

     public void play(Uri uri) {
         try {
             releasePlayer();
             mediaPlayer = new MediaPlayer();
             mediaPlayer.setDataSource(this, uri);
             mediaPlayer.prepare();
             mediaPlayer.start();
             currentMusicUri = uri;

             if (trackChangeListener != null) {
                 File f = new File(uri.getPath());
                 trackChangeListener.onTrackChange(f);
             }

             mediaPlayer.setOnCompletionListener(mp -> {
                 Log.d(TAG, "Track completed, mode=" + repeatMode);
                 switch (repeatMode) {
                     case "REPEAT_ONE":
                         play(currentMusicUri); // 重播当前
                         break;
                     case "SHUFFLE":
                         File randomTrack = getRandomTrack();
                         if (randomTrack != null) {
                             play(Uri.fromFile(randomTrack));
                         }
                         break;
                     case "SEQUENTIAL":
                     default:
                         playNext();
                         break;
                 }
             });

             startForeground(1, buildNotification(new File(uri.getPath()).getName()));
         } catch (IOException e) {
             Log.e(TAG, "播放失败: " + e.getMessage());
         }
     }

     public void resume() {
         if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
             mediaPlayer.start();
         }
     }

     public void pause() {
         if (mediaPlayer != null && mediaPlayer.isPlaying()) {
             mediaPlayer.pause();
         }
     }

     public void stop() {
         if (mediaPlayer != null) {
             mediaPlayer.stop();
             mediaPlayer.release();
             mediaPlayer = null;
         }
         stopForeground(true);
     }

     public void seekTo(int ms) {
         if (mediaPlayer != null) {
             mediaPlayer.seekTo(ms);
         }
     }

     public int getCurrentPosition() {
         return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
     }

     public int getDuration() {
         return mediaPlayer != null ? mediaPlayer.getDuration() : 0;
     }

     public void playNext() {
         if (playList.isEmpty()) return;

         if (repeatMode.equals("SHUFFLE")) {
             File randomTrack = getRandomTrack();
             if (randomTrack != null) {
                 play(Uri.fromFile(randomTrack));
             }
             return;
         }

         currentIndex++;
         if (currentIndex >= playList.size()) currentIndex = 0;

         File next = playList.get(currentIndex);
         play(Uri.fromFile(next));
     }

     private File getRandomTrack() {
         if (playList.isEmpty()) return null;
         Random random = new Random();
         currentIndex = random.nextInt(playList.size());
         return playList.get(currentIndex);
     }

     private void releasePlayer() {
         if (mediaPlayer != null) {
             mediaPlayer.release();
             mediaPlayer = null;
         }
     }

     @Override
     public void onDestroy() {
         releasePlayer();
         super.onDestroy();
     }
 }
