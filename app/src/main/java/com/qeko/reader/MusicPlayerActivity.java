package com.qeko.reader;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.util.DisplayMetrics;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;



public class MusicPlayerActivity extends Activity {

    private TextView tvSongTitle, tvCurrentTime;
    private SeekBar seekBar;
    private Button btnPlayPause, btnNext,  btnRepeatMode;

    private MediaPlayer mediaPlayer;
    private List<File> musicFiles = new ArrayList<>();
    private int currentIndex = 0;
    private Handler handler = new Handler();
    private Runnable updateRunnable;

    private enum RepeatMode {SEQUENTIAL, SHUFFLE, REPEAT_ONE}
    private RepeatMode repeatMode = RepeatMode.SEQUENTIAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);

        // 设置窗口高度为屏幕的 1/5
        LinearLayout layout = findViewById(R.id.musicPlayerRoot);
        layout.post(() -> {
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            layout.getLayoutParams().height = screenHeight / 5;
            layout.requestLayout();
        });

        tvSongTitle = findViewById(R.id.tvSongTitle);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        seekBar = findViewById(R.id.seekBar);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
/*        btnPrev = findViewById(R.id.btnPrev);*/
        btnRepeatMode = findViewById(R.id.btnRepeatMode);

        String filePath = getIntent().getStringExtra("filePath");
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
                    initPlayer(currentIndex);
                }
            }
        }

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnNext.setOnClickListener(v -> playNext());
//        btnPrev.setOnClickListener(v -> playPrev());
        btnRepeatMode.setOnClickListener(v -> cycleRepeatMode());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private boolean isMusicFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac");
    }

    private void initPlayer(int index) {

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }

        File file = musicFiles.get(index);
        mediaPlayer = MediaPlayer.create(this, android.net.Uri.fromFile(file));
        mediaPlayer.setOnCompletionListener(mp -> playNext());

        tvSongTitle.setText(file.getName());
//        Log.d("TAG", "initPlayer: "+tvSongTitle.getText());
        seekBar.setMax(mediaPlayer.getDuration());

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    seekBar.setProgress(mediaPlayer.getCurrentPosition());
                    tvCurrentTime.setText(formatTime(mediaPlayer.getCurrentPosition()) + " / " + formatTime(mediaPlayer.getDuration()));
                }
                handler.postDelayed(this, 500);
            }
        };

        mediaPlayer.start();
        btnPlayPause.setText("⏸️");
        handler.post(updateRunnable);
    }

    private void togglePlayPause() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                btnPlayPause.setText("▶️");
            } else {
                mediaPlayer.start();
                btnPlayPause.setText("⏸️");
            }
        }
    }

    private void playNext() {
        switch (repeatMode) {
            case SHUFFLE:
                currentIndex = new Random().nextInt(musicFiles.size());
                break;
            case REPEAT_ONE:
                // index unchanged
                break;
            case SEQUENTIAL:
            default:
                currentIndex = (currentIndex + 1) % musicFiles.size();
                break;
        }
        initPlayer(currentIndex);
    }

/*    private void playPrev() {
        currentIndex = (currentIndex - 1 + musicFiles.size()) % musicFiles.size();
        initPlayer(currentIndex);
    }*/

    private void cycleRepeatMode() {
        switch (repeatMode) {
            case SEQUENTIAL:
                repeatMode = RepeatMode.SHUFFLE;
                btnRepeatMode.setText("🔀");
                break;
            case SHUFFLE:
                repeatMode = RepeatMode.REPEAT_ONE;
                btnRepeatMode.setText("🔂");
                break;
            case REPEAT_ONE:
                repeatMode = RepeatMode.SEQUENTIAL;
                btnRepeatMode.setText("🔁");
                break;
        }
    }

    private String formatTime(int millis) {
        int seconds = millis / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateRunnable);
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
    }
}
