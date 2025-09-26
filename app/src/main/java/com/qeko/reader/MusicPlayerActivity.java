package com.qeko.reader;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class MusicPlayerActivity extends AppCompatActivity {
    private MusicService musicService;
    private boolean isBound = false;

    private static final MusicServiceConnector instance = new MusicServiceConnector();

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d("TAG", "onServiceConnected: ");
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;

            String filePath = getIntent().getStringExtra("filePath");
            if (filePath != null) {
                musicService.setMusicFromFilePath(filePath);
                tvSongTitle.setText( new File(currentMusicUri.getPath()).getName());
                startSeekBarUpdater();
                btnPlayPause.setText("⏸️");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
        }
    };


    private TextView tvSongTitle, tvCurrentTime;
    private SeekBar seekBar;
    private Button btnPlayPause, btnNext, btnRepeatMode;




    private Handler handler = new Handler();
    private Runnable updateRunnable;

    private Uri currentMusicUri;

    private enum RepeatMode { SEQUENTIAL, SHUFFLE, REPEAT_ONE }
    private RepeatMode repeatMode = RepeatMode.SEQUENTIAL;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);
        Log.d("TAG", "onCreate1: ");
        tvSongTitle = findViewById(R.id.tvSongTitle);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        seekBar = findViewById(R.id.seekBar);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        btnRepeatMode = findViewById(R.id.btnRepeatMode);
        Log.d("TAG", "onCreate2: ");
        String filePath = getIntent().getStringExtra("filePath");
        if (filePath != null) {
            currentMusicUri = Uri.fromFile(new File(filePath));
        }
        Log.d("TAG", "onCreate3: ");
        Intent intent = new Intent(this, MusicService.class);
        startService(intent);              // 保证服务在后台运行
        bindService(intent, connection, BIND_AUTO_CREATE); // 绑定服务，方便控制播放
        Log.d("TAG", "onCreate4: ");
        btnPlayPause.setOnClickListener(v -> {
            if (!isBound || musicService == null) return;

            if (musicService.isPlaying()) {
                // 正在播 → 暂停
                musicService.pause();
                btnPlayPause.setText("▶️");
            } else {
                // 暂停状态 → 继续播
                if (musicService.hasTrackLoaded()) {
                    musicService.resume();
                } else if (currentMusicUri != null) {
                    // 第一次播放
                    musicService.play(currentMusicUri);
                }
                tvSongTitle.setText(new File(currentMusicUri.getPath()).getName());
                btnPlayPause.setText("⏸️");
                startSeekBarUpdater();
            }
        });



        btnNext.setOnClickListener(v -> {
            musicService.playNext();
            // TODO: 这里可以调用 musicService.playNext() 等方法，需在服务实现
            tvSongTitle.setText( new File(currentMusicUri.getPath()).getName());
            startSeekBarUpdater();
        });

        btnRepeatMode.setOnClickListener(v -> cycleRepeatMode());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && isBound && musicService != null) {
                    musicService.seekTo(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }
    private void startSeekBarUpdater() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isBound && musicService != null && musicService.isPlaying()) {
                    int currentPos = musicService.getCurrentPosition();
                    int duration = musicService.getDuration();
                    seekBar.setMax(duration);
                    seekBar.setProgress(currentPos);
                    tvCurrentTime.setText(formatTime(currentPos) + " / " + formatTime(duration));
                }
                handler.postDelayed(this, 500);
            }
        };
        handler.post(updateRunnable);
    }

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
        if (isBound && musicService != null) {
            musicService.setRepeatMode(repeatMode.name());
        }
    }

    private String formatTime(int millis) {
        int seconds = millis / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }


    @Override
    protected void onStart() {
        Log.d("TAG", "onStart: ");
        super.onStart();
        MusicServiceConnector.getInstance().bind(this, service -> {
            musicService = service;

            // 注册监听器
            musicService.setOnTrackChangeListener(newTrack -> runOnUiThread(() -> {
                currentMusicUri = Uri.fromFile(newTrack);
                tvSongTitle.setText(newTrack.getName());
                startSeekBarUpdater();
            }));

/*            String filePath = getIntent().getStringExtra("filePath");
            if (filePath != null) {
                musicService.setMusicFromFilePath(filePath);
            }*/
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        MusicServiceConnector.getInstance().unbind(this);
    }


}
