package com.qeko.reader;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public  class KindleReaderActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TODO: Add Kindle format reader implementation here
        Toast.makeText(this, "Kindle reader not yet implemented", Toast.LENGTH_SHORT).show();
        finish();
    }
}