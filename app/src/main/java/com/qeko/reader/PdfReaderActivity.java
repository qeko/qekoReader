package com.qeko.reader;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;


public class PdfReaderActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TODO: Add mobi reader implementation here
        Toast.makeText(this, "MOBI reader not yet implemented", Toast.LENGTH_SHORT).show();
        finish();
    }
}


