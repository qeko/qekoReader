package com.qeko.reader;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.util.DisplayMetrics;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class MusicPlayerActivity extends Activity {

    private TextView tvSongTitle, tvCurrentTime;
    private SeekBar seekBar;
    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler();

    private ArrayList<File> musicFiles;
    private int currentIndex = 0;

    private Runnable updateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);

        // 设置窗口为屏幕下方1/5高度
        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = (int) (dm.heightPixels * 0.2);
        params.gravity = Gravity.BOTTOM;
        window.setAttributes(params);

        tvSongTitle = findViewById(R.id.tvSongTitle);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        seekBar = findViewById(R.id.seekBar);

        musicFiles = new ArrayList<>();
        String filePath = getIntent().getStringExtra("filePath");

        if (filePath != null) {
            File currentFile = new File(filePath);
            File parentDir = currentFile.getParentFile();
            if (parentDir != null && parentDir.isDirectory()) {
                File[] files = parentDir.listFiles();
                for (File f : files) {
                    if (isMusicFile(f.getName())) {
                        musicFiles.add(f);
                    }
                }
                currentIndex = musicFiles.indexOf(currentFile);
                if (currentIndex < 0) currentIndex = 0;
                playMusic(musicFiles.get(currentIndex));
            }
        }
    }

    private void playMusic(File file) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }

        tvSongTitle.setText(file.getName());

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 设置最大值
        seekBar.setMax(mediaPlayer.getDuration());

        // 进度条监听
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean userTouch = false;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && userTouch) {
                    mediaPlayer.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userTouch = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userTouch = false;
            }
        });

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null) {
                    int current = mediaPlayer.getCurrentPosition();
                    int total = mediaPlayer.getDuration();
                    seekBar.setProgress(current);
                    tvCurrentTime.setText(formatTime(current) + " / " + formatTime(total));
                    handler.postDelayed(this, 500);
                }
            }
        };

        handler.post(updateRunnable);
    }

    private boolean isMusicFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac");
    }

    private String formatTime(int millis) {
        int totalSeconds = millis / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        handler.removeCallbacks(updateRunnable);
    }
}


/*

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;
import android.view.WindowManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MusicPlayerActivity extends Activity {

    private Button btnPlayPause, btnNext, btnMode;
    private MediaPlayer mediaPlayer;
    private List<File> musicFiles = new ArrayList<>();
    private int currentIndex = 0;

    private enum Mode { SEQUENTIAL, RANDOM, REPEAT_ONE }
    private Mode playMode = Mode.SEQUENTIAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);


        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        btnMode = findViewById(R.id.btnMode);

        initMusicFiles();
        setupPlayer();

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnNext.setOnClickListener(v -> playNext());
        btnMode.setOnClickListener(v -> toggleMode());

*/
/*        // 设置高度为屏幕的1/5
        WindowManager.LayoutParams params = getWindow().getAttributes();
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = metrics.heightPixels / 5;
        getWindow().setAttributes(params);*//*


        // 设置窗口尺寸为屏幕高度的1/5
        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = (int) (dm.heightPixels * 0.2);  // 屏幕下方1/5
        params.gravity = Gravity.BOTTOM;
        window.setAttributes(params);

    }

    private void initMusicFiles() {
        String filePath = getIntent().getStringExtra("filePath");
        if (filePath == null) {
            Toast.makeText(this, "未提供音乐文件路径", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        File current = new File(filePath);
        File parent = current.getParentFile();

        if (parent != null && parent.isDirectory()) {
            for (File f : parent.listFiles()) {
                if (f.isFile() && isMusicFile(f)) {
                    musicFiles.add(f);
                }
            }

            currentIndex = musicFiles.indexOf(current);
            if (currentIndex < 0) currentIndex = 0;
        }
    }

    private boolean isMusicFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac");
    }

    private void setupPlayer() {
        if (musicFiles.isEmpty()) {
            Toast.makeText(this, "没有音乐文件", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        playFile(musicFiles.get(currentIndex));
    }

    private void playFile(File file) {
        stopPlayer();

        mediaPlayer = MediaPlayer.create(this, android.net.Uri.fromFile(file));
        if (mediaPlayer == null) {
            Toast.makeText(this, "无法播放: " + file.getName(), Toast.LENGTH_SHORT).show();
            return;
        }

        mediaPlayer.setOnCompletionListener(mp -> {
            if (playMode == Mode.REPEAT_ONE) {
                playFile(musicFiles.get(currentIndex));
            } else {
                playNext();
            }
        });

        mediaPlayer.start();
        btnPlayPause.setText("⏸️");
    }

    private void stopPlayer() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) return;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setText("▶️");
        } else {
            mediaPlayer.start();
            btnPlayPause.setText("⏸️");
        }
    }

    private void playNext() {
        if (musicFiles.isEmpty()) return;

        switch (playMode) {
            case SEQUENTIAL:
                currentIndex = (currentIndex + 1) % musicFiles.size();
                break;
            case RANDOM:
                currentIndex = new Random().nextInt(musicFiles.size());
                break;
            case REPEAT_ONE:
                // index unchanged
                break;
        }

        playFile(musicFiles.get(currentIndex));
    }

    private void toggleMode() {
        switch (playMode) {
            case SEQUENTIAL:
                playMode = Mode.RANDOM;
                btnMode.setText("🔀");
                break;
            case RANDOM:
                playMode = Mode.REPEAT_ONE;
                btnMode.setText("🔂");
                break;
            case REPEAT_ONE:
                playMode = Mode.SEQUENTIAL;
                btnMode.setText("🔁");
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPlayer();
    }
}
*/
