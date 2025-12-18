package com.qeko.reader;



import android.content.pm.ActivityInfo;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
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
    private GestureDetector gestureDetector;
    private SeekBar seekBar;
    private Button btnSwitchAudioTrack,btnRepeat;//btnPlayPause,
    private Switch switchOrientation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        // 隐藏状态栏，保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        // 初始化手势检测器
        gestureDetector = new GestureDetector(this, new GestureListener());
        videoLayout = findViewById(R.id.vlcVideoLayout);

//        btnFavrite  = findViewById(R.id.btn_favority);
        btnSwitchAudioTrack = findViewById(R.id.btnSwitchAudioTrack);

//        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnRepeat = findViewById(R.id.btn_repeat);
        btnRepeat.setOnClickListener(v -> setRepeat());
        btnRepeat.setTextColor(Color.BLUE);
        seekBar = findViewById(R.id.seekBar);

        libVLC = new LibVLC(this, new ArrayList<>());
        mediaPlayer = new MediaPlayer(libVLC);
        mediaPlayer.attachViews(videoLayout, null, false, false);
        mediaPlayer.setVideoScale( MediaPlayer.ScaleType.SURFACE_FILL);


/*     mediaPlayer.setEventListener(event -> {
            if (event.type == MediaPlayer.Event.EndReached) {
                playNextVideo();
            }

        });*/

        mediaPlayer.setEventListener(event -> {
            switch (event.type) {

                case MediaPlayer.Event.Vout:   //版本低时不生效
                    Media.VideoTrack track = mediaPlayer.getCurrentVideoTrack();
                    if (track != null) {
                        int w = track.width;
                        int h = track.height;
                        runOnUiThread(() -> handleOrientationByRatio(w, h));
                    }
                    break;

                case MediaPlayer.Event.EndReached:
                    runOnUiThread(this::playNextVideo);
                    break;
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


        // 播放时默认选择第二个音轨并隐藏字幕
        new android.os.Handler().postDelayed(() -> {
            switchAudioTrack();
            mediaPlayer.setSpuTrack(-1); // 不显示字幕

            if(mediaPlayer.getAudioTracksCount()<3) btnSwitchAudioTrack.setVisibility(View.GONE);
            else btnSwitchAudioTrack.setVisibility(View.VISIBLE);

        }, 1000); // 延迟1秒执行

        btnSwitchAudioTrack.setOnClickListener(v -> switchName());
        // 下一首按钮


        switchOrientation = findViewById(R.id.switch_orientation);

        // 初始化：根据当前方向设置状态
        int orientation = getRequestedOrientation();
        switchOrientation.setChecked(
                orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        );

        switchOrientation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            userLockedOrientation = true;   // ★ 用户接管

            if (isChecked) {
                setLandscape();
            } else {
                setPortrait();
            }
        });

       /* switchOrientation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // 横屏
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                // 竖屏
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        });*/

        Log.d("TAG", "setRepeat: "+btnRepeat.getTextColors());
    }

    // 是否已根据视频比例自动处理过
    private boolean autoOrientationHandled = false;

    // 用户是否手动锁定了方向
    private boolean userLockedOrientation = false;
    private void handleOrientationByRatio(int width, int height) {
        if (width <= 0 || height <= 0) return;

        // 用户手动切换过 → 不再自动
        if (userLockedOrientation) return;

        // 已自动处理过 → 不重复
        if (autoOrientationHandled) return;

        autoOrientationHandled = true;

        float ratio = (float) width / height;
        Log.d("TAG", "handleOrientationByRatio: ="+ratio);
        if (ratio >= 1.5f) {
            setLandscape();
            switchOrientation.setChecked(true);
        } else {
            setPortrait();
            switchOrientation.setChecked(false);
        }
    }


    private void setLandscape() {
        if (getRequestedOrientation()
                != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {

            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    private void setPortrait() {
        if (getRequestedOrientation()
                != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {

            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    private void setRepeat() {

        if (btnRepeat.getTextColors().toString().indexOf("7829368")>0)
        {
            this.btnRepeat.setTextColor(Color.BLUE);
        }else
        {
            this.btnRepeat.setTextColor(Color.YELLOW);
        }

        Log.d("TAG", "setRepeat: "+btnRepeat.getTextColors());
    }

    private boolean isVideoFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") ||
                lower.endsWith(".mov") || lower.endsWith(".flv") || lower.endsWith(".webm");
    }

    private void playVideo(int index) {

        if (index >= 0 && index < videoFiles.size()) {
            autoOrientationHandled = false;   // ★ 必须重置
            File file = videoFiles.get(index);
            Media media = new Media(libVLC, Uri.fromFile(file));
            mediaPlayer.setMedia(media);
            media.release();
            mediaPlayer.play();
            // 播放时默认选择第二个音轨并隐藏字幕
            new android.os.Handler().postDelayed(() -> {
                switchAudioTrack();
                mediaPlayer.setSpuTrack(-1); // 不显示字幕

                Media.VideoTrack track = mediaPlayer.getCurrentVideoTrack();
                if (track != null) {
                    int w = track.width;
                    int h = track.height;
                    runOnUiThread(() -> handleOrientationByRatio(w, h));
                }

                if(mediaPlayer.getAudioTracksCount()<3) btnSwitchAudioTrack.setVisibility(View.GONE);
                else btnSwitchAudioTrack.setVisibility(View.VISIBLE);
            }, 1000); // 延迟1秒执行
        }

    }

    private void playNextVideo() {
        Log.d("TAG", "btnRepeat: "+btnRepeat.getTextColors());
        if (btnRepeat.getTextColors().toString().indexOf("7829368")>0)
        {
            currentIndex = (currentIndex + 1) % videoFiles.size();
            btnRepeat.setTextColor(Color.YELLOW);
        } else{
            btnRepeat.setTextColor(Color.BLUE);
        }
        playVideo(currentIndex);
    }

    @SuppressLint("ResourceAsColor")
    private void switchName() {
        this.btnRepeat.setVisibility(View.VISIBLE);
        if(btnSwitchAudioTrack.getText().equals("原唱"))
        {
            btnSwitchAudioTrack.setBackgroundColor(android.R.color.holo_blue_dark);
            btnSwitchAudioTrack.setText("伴唱");
        }else
        {
            btnSwitchAudioTrack.setText("原唱");
        }
        switchAudioTrack();
    }

    @SuppressLint("ResourceAsColor")
    private void switchAudioTrack() {
        MediaPlayer.TrackDescription[] audioTracks = mediaPlayer.getAudioTracks();
        int currentTrackIndex = mediaPlayer.getAudioTrack();
        if (audioTracks != null && audioTracks.length > 2) {
            if(btnSwitchAudioTrack.getText().equals("原唱"))
            {
                if(currentTrackIndex != 1)mediaPlayer.setAudioTrack(1);
//                mediaPlayer.setAudioTrack(0);
            }else
            {
                if(currentTrackIndex != 2)mediaPlayer.setAudioTrack(2);
//                mediaPlayer.setAudioTrack(3);
            }

        }
    }



    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null) {
//            savePlaybackPosition();
            mediaPlayer.pause();

//            progressHandler.removeCallbacks(updateProgressTask); // 停止更新
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayer != null) {
            mediaPlayer.play();
        }
//        progressHandler.post(updateProgressTask); // 开始更新
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        private static final int SWIPE_THRESHOLD = 200; // 滑动的最小距离
        private static final int VELOCITY_THRESHOLD = 200; // 滑动的最小速度

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // 单击切换播放/暂停
            togglePlayPause();
            return true;
        }

        int iFligDown = 0;
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float deltaX = e2.getX() - e1.getX();
            float deltaY = e2.getY() - e1.getY();

            if (Math.abs(deltaX) > Math.abs(deltaY)) {
                // 水平滑动
                if (Math.abs(deltaX) > SWIPE_THRESHOLD && Math.abs(velocityX) > VELOCITY_THRESHOLD) {
                    if (deltaX > 0) {
                        onSwipeRight();
                     } else {
                        onSwipeLeft();

                    }
                    return true;
                }
            } else {
                // 垂直滑动
                if (Math.abs(deltaY) > SWIPE_THRESHOLD && Math.abs(velocityY) > VELOCITY_THRESHOLD) {
                    if (deltaY > 0) {
                        currentIndex = currentIndex-1;

                    } else {
                        currentIndex = currentIndex+1;

                    }
                    playVideo(currentIndex);
                    btnRepeat.setTextColor(Color.BLUE);//使重唱无效
                    return true;
                }
            }

            return false;
        }

    }

    // 左滑时快进5秒
    private boolean msg=true;
    private void onSwipeLeft() {
        long currentTime = mediaPlayer.getTime(); // 获取当前播放时间
        mediaPlayer.setTime(currentTime - 5000);  // 快进5秒

        if(msg)Toast.makeText(this, "后退5’", Toast.LENGTH_SHORT).show();
        msg = false;
    }

    private void onSwipeRight() {
        long currentTime = mediaPlayer.getTime(); // 获取当前播放时间
        mediaPlayer.setTime(currentTime + 5000);  //  5秒
        if(msg)Toast.makeText(this, "前进5‘", Toast.LENGTH_SHORT).show();
        msg = false;
    }


    private boolean isPlaying = true;  // 当前播放状态
    private void togglePlayPause() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                isPlaying = false;
                Toast.makeText(this, "暂停", Toast.LENGTH_SHORT).show();
            } else {
                mediaPlayer.play();
                isPlaying = true;
//                Toast.makeText(this, "播放", Toast.LENGTH_SHORT).show();
            }
        }
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
