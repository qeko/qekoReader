package com.qeko.reader;
import android.content.res.AssetManager;
import android.util.Log;
import android.view.WindowManager;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class ImageViewerActivity extends Activity {

    private ImageView imageView;
    private Button btnNext, btnPrev, btnPlayPause, btnFullscreen, btnDelete, btnMode;
    private TextView tvFileName;
    private RecyclerView thumbnailRecyclerView;

    private List<File> imageFiles = new ArrayList<>();
    private int currentIndex = 0;

    private boolean isPlaying = true;
    private boolean isFullscreen = false;
    private boolean isFilenameMode = true;

    private final Handler handler = new Handler();
    private final int SLIDESHOW_DELAY = 3000;

    private final Matrix matrix = new Matrix();
    private float scaleFactor = 1.0f;
    private float lastTouchX = 0, lastTouchY = 0;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;

    private final Runnable slideshowRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPlaying) {
                showNextImage();
                handler.postDelayed(this, SLIDESHOW_DELAY);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);

        imageView = findViewById(R.id.imageView);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrev);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnFullscreen = findViewById(R.id.btnFullscreen);
        btnDelete = findViewById(R.id.btnDelete);
        btnMode = findViewById(R.id.btnMode);
        tvFileName = findViewById(R.id.tvFileName);
        thumbnailRecyclerView = findViewById(R.id.thumbnailRecyclerView);

        thumbnailRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
//        ThumbnailAdapter adapter = new ThumbnailAdapter(imageFiles, this::jumpToImage);
        ThumbnailAdapter adapter = new ThumbnailAdapter(this, imageFiles, this::jumpToImage);
        thumbnailRecyclerView.setAdapter(adapter);

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scale = detector.getScaleFactor();
                scaleFactor *= scale;
                scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 5.0f));
                matrix.postScale(scale, scale, detector.getFocusX(), detector.getFocusY());
                imageView.setImageMatrix(matrix);
                return true;
            }
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        showPrevImage();
                    } else {
                        showNextImage();
                    }
                    return true;
                }
                return false;
            }
        });

        imageView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);

            if (scaleFactor > 1.0f) { // 放大后才能拖动
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - lastTouchX;
                        float dy = event.getY() - lastTouchY;
                        matrix.postTranslate(dx, dy);
                        imageView.setImageMatrix(matrix);
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        break;
                }
            }
            return true;
        });

        btnNext.setOnClickListener(v -> showNextImage());
        btnPrev.setOnClickListener(v -> showPrevImage());
        btnDelete.setOnClickListener(v -> deleteCurrentImage());
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnFullscreen.setOnClickListener(v -> toggleFullscreen());
        btnMode.setOnClickListener(v -> toggleDisplayMode());

        loadImagesFromIntent();
        imageView.post(() -> displayImage(imageFiles.get(currentIndex))); // 修复首张空白

        btnPlayPause.setText("⏸️");
        handler.postDelayed(slideshowRunnable, SLIDESHOW_DELAY);
    }

    private void deleteCurrentImage() {
        if (imageFiles.isEmpty()) return;

        File file = imageFiles.get(currentIndex);
        if (file.exists() && file.delete()) {
            imageFiles.remove(currentIndex);
            if (currentIndex >= imageFiles.size()) currentIndex = 0;
            imageView.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                if (!imageFiles.isEmpty()) {
                    displayImage(imageFiles.get(currentIndex));
                    imageView.animate().alpha(1f).setDuration(300).start();
                }
            }).start();
        }
    }

    private void jumpToImage(int position) {
        if (position >= 0 && position < imageFiles.size()) {
            currentIndex = position;
            displayImage(imageFiles.get(currentIndex));
        }
    }

    private void toggleDisplayMode() {
        isFilenameMode = !isFilenameMode;
        tvFileName.setVisibility(isFilenameMode ? View.VISIBLE : View.GONE);
        thumbnailRecyclerView.setVisibility(isFilenameMode ? View.GONE : View.VISIBLE);
    }

    private void togglePlayPause() {
        isPlaying = !isPlaying;
        btnPlayPause.setText(isPlaying ? "⏸️" : "▶️");
        if (isPlaying) {
            handler.post(slideshowRunnable);
        } else {
            handler.removeCallbacks(slideshowRunnable);
        }
    }

    private void toggleFullscreen() {
        if (isFullscreen) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            btnFullscreen.setText("⛶");
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            btnFullscreen.setText("🖥");
        }
        isFullscreen = !isFullscreen;
    }

    private void loadImagesFromIntent() {
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
                }
            }
        }
    }

    private boolean isImageFile(String name) {
        name = name.toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp");
    }

    private void showNextImage() {
        if (imageFiles.isEmpty()) return;
        currentIndex = (currentIndex + 1) % imageFiles.size();
        displayImage(imageFiles.get(currentIndex));
    }

    private void showPrevImage() {
        if (imageFiles.isEmpty()) return;
        currentIndex = (currentIndex - 1 + imageFiles.size()) % imageFiles.size();
        displayImage(imageFiles.get(currentIndex));
    }

    private void displayImage(File file) {
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) return;

        imageView.setImageBitmap(bitmap);
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        matrix.reset();

        int viewWidth = imageView.getWidth();
        int viewHeight = imageView.getHeight();
        int imageWidth = bitmap.getWidth();
        int imageHeight = bitmap.getHeight();

        float scale, dx = 0, dy = 0;
        if ((float) imageWidth / imageHeight > (float) viewWidth / viewHeight) {
            scale = (float) viewWidth / imageWidth;
            dy = (viewHeight - imageHeight * scale) / 2f;
        } else {
            scale = (float) viewHeight / imageHeight;
            dx = (viewWidth - imageWidth * scale) / 2f;
        }

        scaleFactor = scale;
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);

        imageView.setImageMatrix(matrix);

        if (isFilenameMode) {
            tvFileName.setText(file.getName());
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(slideshowRunnable);
        super.onDestroy();
    }
}
