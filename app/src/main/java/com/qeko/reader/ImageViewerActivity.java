package com.qeko.reader;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.Toast;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import com.bumptech.glide.Glide;

public  class ImageViewerActivity extends Activity {

    private ImageView imageView;
    private List<File> imageFiles = new ArrayList<>();
    private int currentIndex = 0;
    private Handler handler = new Handler();
    private Runnable slideshowRunnable;
    private static final int SLIDESHOW_INTERVAL_MS = 3000;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        imageView = findViewById(R.id.imageView);

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
                    startSlideshow();
                }
            }
        } else {
            Toast.makeText(this, "No image file provided", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    private void showImage(int index) {
        if (imageFiles.isEmpty()) return;
        File file = imageFiles.get(index);
        Glide.with(this).load(file).into(imageView);
    }

    private void startSlideshow() {
        slideshowRunnable = new Runnable() {
            @Override
            public void run() {
                currentIndex = (currentIndex + 1) % imageFiles.size();
                showImage(currentIndex);
                handler.postDelayed(this, SLIDESHOW_INTERVAL_MS);
            }
        };
        handler.postDelayed(slideshowRunnable, SLIDESHOW_INTERVAL_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(slideshowRunnable);
    }
}