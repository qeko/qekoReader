package com.qeko.reader;


import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PdfRendererActivity extends AppCompatActivity {

    private ImageView pdfPageImage;
    private RecyclerView rvTextLines;
    private Button btnPrevPage, btnNextPage, btnRead;

    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPage;
    private ParcelFileDescriptor parcelFileDescriptor;
    private int pageIndex = 0;

    private PDDocument pdDocument;
    private List<String> currentPageLines = new ArrayList<>();
    private PdfLineAdapter adapter;

    private TextToSpeech tts;
    private int ttsLineIndex = 0;
    private boolean isReading = false;

    private String pdfPath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_reader_advanced);

        pdfPageImage = findViewById(R.id.pdfPageImage);
        rvTextLines = findViewById(R.id.rvTextLines);
        btnPrevPage = findViewById(R.id.btnPrevPage);
        btnNextPage = findViewById(R.id.btnNextPage);
        btnRead = findViewById(R.id.btnRead);

        pdfPath = getIntent().getStringExtra("pdf_path");
        if (pdfPath == null) {
            Toast.makeText(this, "无效的PDF路径", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            parcelFileDescriptor = ParcelFileDescriptor.open(new File(pdfPath), ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(parcelFileDescriptor);

            pdDocument = PDDocument.load(new File(pdfPath));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "打开PDF失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        adapter = new PdfLineAdapter(currentPageLines);
        rvTextLines.setLayoutManager(new LinearLayoutManager(this));
        rvTextLines.setAdapter(adapter);

        btnPrevPage.setOnClickListener(v -> {
            if (pageIndex > 0) {
                pageIndex--;
                openPage(pageIndex);
            }
        });

        btnNextPage.setOnClickListener(v -> {
            if (pageIndex < pdfRenderer.getPageCount() - 1) {
                pageIndex++;
                openPage(pageIndex);
            }
        });

        btnRead.setOnClickListener(v -> {
            if (isReading) {
                stopReading();
            } else {
                startReading();
            }
        });

        initTTS();

        openPage(pageIndex);
    }

    private void openPage(int index) {
        if (currentPage != null) {
            currentPage.close();
        }
        currentPage = pdfRenderer.openPage(index);

        int width = getResources().getDisplayMetrics().densityDpi / 72 * currentPage.getWidth();
        int height = getResources().getDisplayMetrics().densityDpi / 72 * currentPage.getHeight();

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

        pdfPageImage.setImageBitmap(bitmap);

        loadPageText(index);
        stopReading();
    }

    private void loadPageText(int pageIndex) {
        currentPageLines.clear();
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageIndex + 1); // PDFBox页码从1开始
            stripper.setEndPage(pageIndex + 1);
            String pageText = stripper.getText(pdDocument);

            String[] lines = pageText.split("\\r?\\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    currentPageLines.add(line.trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        adapter.notifyDataSetChanged();
        adapter.setHighlightIndex(-1);
        ttsLineIndex = 0;
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());
            }
        });
        tts.setOnUtteranceProgressListener(new  UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onError(String utteranceId) {}
            @Override
            public void onDone(String utteranceId) {
                runOnUiThread(() -> {
                    ttsLineIndex++;
                    if (ttsLineIndex < currentPageLines.size() && isReading) {
                        readCurrentLine();
                    } else {
                        stopReading();
                    }
                });
            }
        });
    }

    private void startReading() {
        if (currentPageLines.isEmpty()) return;
        isReading = true;
        btnRead.setText("停止朗读");
        ttsLineIndex = 0;
        readCurrentLine();
    }

    private void readCurrentLine() {
        if (ttsLineIndex < currentPageLines.size()) {
            adapter.setHighlightIndex(ttsLineIndex);
            rvTextLines.scrollToPosition(ttsLineIndex);

            String text = currentPageLines.get(ttsLineIndex);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "line_" + ttsLineIndex);
        }
    }

    private void stopReading() {
        isReading = false;
        btnRead.setText("开始朗读");
        if (tts != null) tts.stop();
        adapter.setHighlightIndex(-1);
    }

    @Override
    protected void onDestroy() {
        if (currentPage != null) currentPage.close();
        if (pdfRenderer != null) pdfRenderer.close();
        if (parcelFileDescriptor != null) {
            try {
                parcelFileDescriptor.close();
            } catch (IOException e) { e.printStackTrace(); }
        }
        if (pdDocument != null) {
            try {
                pdDocument.close();
            } catch (IOException e) { e.printStackTrace(); }
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
