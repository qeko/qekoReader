package com.qeko.reader;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.ImageView;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageViewerActivity extends Activity {

    private ImageView imageView;
    private List<File> imageFiles = new ArrayList<>();
    private int currentIndex = 0;

    private Handler handler = new Handler();
    private boolean isAutoPlay = true;
    private Runnable slideShowRunnable;

    private Button btnPlayPause, btnPrev, btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        imageView = findViewById(R.id.imageView);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);

        String filePath = getIntent().getStringExtra("filePath");
        if (filePath != null) {
            File currentImage = new File(filePath);
            File parentDir = currentImage.getParentFile();
            if (parentDir != null && parentDir.isDirectory()) {
                File[] files = parentDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && isImageFile(f.getName())) {
                            imageFiles.add(f);
                        }
                    }
                    currentIndex = imageFiles.indexOf(currentImage);
                    if (currentIndex < 0) currentIndex = 0;
                    showImage(currentIndex);
                }
            }
        }

        slideShowRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAutoPlay) {
                    currentIndex = (currentIndex + 1) % imageFiles.size();
                    showImage(currentIndex);
                    handler.postDelayed(this, 3000); // 每3秒切换
                }
            }
        };

        handler.postDelayed(slideShowRunnable, 3000);

        btnPlayPause.setOnClickListener(v -> {
            isAutoPlay = !isAutoPlay;
            if (isAutoPlay) {
                btnPlayPause.setText("⏸️");

                handler.postDelayed(slideShowRunnable, 3000);
            } else {

                btnPlayPause.setText("▶️️");
                handler.removeCallbacks(slideShowRunnable);
            }
        });

        btnPrev.setOnClickListener(v -> {
            isAutoPlay = false;
            btnPlayPause.setText("▶️");
            handler.removeCallbacks(slideShowRunnable);
            currentIndex = (currentIndex - 1 + imageFiles.size()) % imageFiles.size();
            showImage(currentIndex);
        });

        btnNext.setOnClickListener(v -> {
            isAutoPlay = false;
            btnPlayPause.setText("▶️");
            handler.removeCallbacks(slideShowRunnable);
            currentIndex = (currentIndex + 1) % imageFiles.size();
            showImage(currentIndex);
        });
    }

    private boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif");
    }

    private void showImage(int index) {
        if (imageFiles.isEmpty()) {
            Toast.makeText(this, "没有图片可显示", Toast.LENGTH_SHORT).show();
            return;
        }
        File image = imageFiles.get(index);
        Glide.with(this).load(image).into(imageView);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(slideShowRunnable);
    }
}
