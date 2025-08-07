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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ImageViewerActivity extends Activity {

    private ImageView imageView;
    private Button btnNext, btnPrev, btnPlayPause, btnFullscreen,btnDelete;

    private List<File> imageFiles = new ArrayList<>();
    private int currentIndex = 0;

    private boolean isPlaying = true;
    private boolean isFullscreen = false;
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

/*        AssetManager assetManager = this.getAssets();
        try (InputStream input = assetManager.open("com/tom_roush/pdfbox/resources/glyphlist/glyphlist.txt")) {
            Log.d("Assets", "glyphlist.txt loaded successfully");
        } catch (IOException e) {
            Log.e("Assets", "glyphlist.txt not found", e);
        }*/

        // 隐藏状态栏，保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);


        imageView = findViewById(R.id.imageView);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrev);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnFullscreen = findViewById(R.id.btnFullscreen);
        btnDelete = findViewById(R.id.btnDelete);

        // 设置缩放手势识别
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

        // 设置左右滑动识别
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
            return true;
        });

        btnNext.setOnClickListener(v -> showNextImage());
        btnPrev.setOnClickListener(v -> showPrevImage());
        btnDelete.setOnClickListener(v -> deleteCurrentImage());

        btnPlayPause.setOnClickListener(v -> {
            isPlaying = !isPlaying;

            btnPlayPause.setText(isPlaying ? "⏸️" : "▶️️");
            if (isPlaying) {
                handler.post(slideshowRunnable);
            } else {
                handler.removeCallbacks(slideshowRunnable);
            }
        });

        btnFullscreen.setOnClickListener(v -> {
            toggleFullscreen();
        });

        loadImagesFromIntent();
        displayImage(imageFiles.get(currentIndex));
        btnPlayPause.setText("⏸️" );
        handler.postDelayed(slideshowRunnable, SLIDESHOW_DELAY);
    }

    private void deleteCurrentImage() {
        isPlaying = false;
        if (imageFiles.isEmpty()) return;

        File file = imageFiles.get(currentIndex);
        if (file.exists() && file.delete()) {
            imageFiles.remove(currentIndex);
            if (currentIndex >= imageFiles.size()) currentIndex = 0;
            showImage(currentIndex);
        }
    }

    private void showImage(int index) {

        if (imageFiles.isEmpty()) return;
        if (index < 0) index = imageFiles.size() - 1;
        if (index >= imageFiles.size()) index = 0;

        currentIndex = index;
        imageView.setImageDrawable(Drawable.createFromPath(imageFiles.get(index).getAbsolutePath()));
        imageView.setImageMatrix(new Matrix()); // Reset any zoom


        btnFullscreen.setOnClickListener(v -> toggleFullscreen());


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
        isPlaying = false;
        if (imageFiles.isEmpty()) return;
        currentIndex = (currentIndex + 1) % imageFiles.size();
        displayImage(imageFiles.get(currentIndex));
    }

    private void showPrevImage() {
        isPlaying = false;
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
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(slideshowRunnable);
        super.onDestroy();
    }
}


/*
package com.qeko.reader;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageViewerActivity extends Activity {

    private ImageView imageView;
    private Button btnPlayPause, btnDelete, btnPrev, btnNext;
    private List<File> imageFiles = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isPlaying = false;
    private Handler handler = new Handler();
    private Runnable slideshowRunnable;
    private boolean isFullscreen = false;
    private Button btnFullscreen;

    private GestureDetector gestureDetector;
    private float scaleFactor = 1.0f;

    private ScaleGestureDetector scaleGestureDetector;
    private Matrix matrix = new Matrix();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        // 隐藏状态栏，保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);

        imageView = findViewById(R.id.imageView);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnDelete = findViewById(R.id.btnDelete);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnFullscreen = findViewById(R.id.btnFullscreen); // 新按钮




        String filePath = getIntent().getStringExtra("filePath");
        if (filePath != null) {
            File currentImage = new File(filePath);
            File parentDir = currentImage.getParentFile();
            if (parentDir != null && parentDir.isDirectory()) {
                File[] files = parentDir.listFiles();
                for (File f : files) {
                    if (isImageFile(f.getName())) {
                        imageFiles.add(f);
                    }
                }
                currentIndex = imageFiles.indexOf(currentImage);
                if (currentIndex < 0) currentIndex = 0;
            }
        }

        showImage(currentIndex);

        btnPlayPause.setOnClickListener(v -> togglePlay());
        btnDelete.setOnClickListener(v -> deleteCurrentImage());
        btnPrev.setOnClickListener(v -> showImage(currentIndex - 1));
        btnNext.setOnClickListener(v -> showImage(currentIndex + 1));

        slideshowRunnable = new Runnable() {
            @Override
            public void run() {
                showImage(currentIndex + 1);
                handler.postDelayed(this, 3000);
            }
        };

        gestureDetector = new GestureDetector(this, new GestureListener());
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        imageView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }




    private void toggleFullscreen() {
        if (isFullscreen) {
            exitFullscreen();
        } else {
            enterFullscreen();
        }
    }

    private void enterFullscreen() {
        isFullscreen = true;
        btnFullscreen.setText("🖥"); // 显示“退出全屏”图标
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void exitFullscreen() {
        isFullscreen = false;
        btnFullscreen.setText("⛶"); // 显示“进入全屏”图标
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }


    private void showImage(int index) {
        if (imageFiles.isEmpty()) return;
        if (index < 0) index = imageFiles.size() - 1;
        if (index >= imageFiles.size()) index = 0;

        currentIndex = index;
        imageView.setImageDrawable(Drawable.createFromPath(imageFiles.get(index).getAbsolutePath()));
        imageView.setImageMatrix(new Matrix()); // Reset any zoom


        btnFullscreen.setOnClickListener(v -> toggleFullscreen());

        // 开始时可默认非全屏
        exitFullscreen();
    }



    private void togglePlay() {
        isPlaying = !isPlaying;
        btnPlayPause.setText(isPlaying ? "⏸️" : "▶️️");
        if (isPlaying) {
            handler.postDelayed(slideshowRunnable, 3000);
        } else {
            handler.removeCallbacks(slideshowRunnable);
        }
    }

    private void deleteCurrentImage() {
        if (imageFiles.isEmpty()) return;

        File file = imageFiles.get(currentIndex);
        if (file.exists() && file.delete()) {
            imageFiles.remove(currentIndex);
            if (currentIndex >= imageFiles.size()) currentIndex = 0;
            showImage(currentIndex);
        }
    }

    private boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".gif") ||
                lower.endsWith(".bmp") || lower.endsWith(".webp");
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scale = detector.getScaleFactor();
            scaleFactor *= scale;
            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f));

            matrix.setScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            imageView.setImageMatrix(matrix);
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();
            if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffX > 0) {
                    showPreviousImage();
                } else {
                    showNextImage();
                }
                return true;
            }
            return false;
        }
    }


    private void resetZoom() {
        scaleFactor = 1.0f;
        matrix.reset();
        imageView.setImageMatrix(matrix);
    }
    private void displayImage(File file) {
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        imageView.setImageBitmap(bitmap);
        resetZoom();

        if (isFullscreen) {
            enterFullscreen();
        }
    }

    private void showPreviousImage() {
        if (currentIndex > 0) {
            currentIndex--;
            displayImage(imageFiles.get(currentIndex));
        }
    }

    private void showNextImage() {
        if (currentIndex < imageFiles.size() - 1) {
            currentIndex++;
            displayImage(imageFiles.get(currentIndex));
        }
    }



}


*/
