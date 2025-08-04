package com.qeko.reader;




import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class VideoPlayerActivity extends Activity {

    private VLCVideoLayout videoLayout;
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private List<File> videoFiles = new ArrayList<>();
    private int currentIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        videoLayout = findViewById(R.id.vlcVideoLayout);

        libVLC = new LibVLC(this, new ArrayList<>());
        mediaPlayer = new MediaPlayer(libVLC);
        mediaPlayer.attachViews(videoLayout, null, false, false);

        mediaPlayer.setEventListener(event -> {
            if (event.type == MediaPlayer.Event.EndReached) {
                playNextVideo();
            }
        });

        String filePath = getIntent().getStringExtra("filePath");
        if (filePath != null) {
            File currentFile = new File(filePath);
            File parentDir = currentFile.getParentFile();
            if (parentDir != null && parentDir.isDirectory()) {
                File[] files = parentDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (isVideoFile(f.getName())) {
                            videoFiles.add(f);
                        }
                    }

                    currentIndex = videoFiles.indexOf(currentFile);
                    if (currentIndex < 0) currentIndex = 0;

                    playVideo(currentIndex);
                }
            }
        } else {
            Toast.makeText(this, "未提供视频路径", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private boolean isVideoFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") ||
                lower.endsWith(".mov") || lower.endsWith(".flv") || lower.endsWith(".webm");
    }

    private void playVideo(int index) {
        if (index >= 0 && index < videoFiles.size()) {
            File file = videoFiles.get(index);
            Media media = new Media(libVLC, Uri.fromFile(file));
            mediaPlayer.setMedia(media);
            media.release();
            mediaPlayer.play();
        }
    }

    private void playNextVideo() {
        currentIndex = (currentIndex + 1) % videoFiles.size();
        playVideo(currentIndex);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mediaPlayer.stop();
        mediaPlayer.detachViews();
        mediaPlayer.release();
        libVLC.release();
    }
}
