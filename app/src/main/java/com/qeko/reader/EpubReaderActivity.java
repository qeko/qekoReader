package com.qeko.reader;



import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/*import com.folioreader.Config;
import com.folioreader.FolioReader;
import com.folioreader.model.HighlightImpl;
import com.folioreader.model.event.SaveHighlightEvent;
import com.folioreader.util.AppUtil;*/
/*
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;*/

public class EpubReaderActivity extends Activity {

  /*  private FolioReader folioReader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String filePath = getIntent().getStringExtra("filePath");
        if (filePath == null || !filePath.endsWith(".epub")) {
            Toast.makeText(this, "无效的EPUB路径", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        folioReader = FolioReader.get()
                .setConfig(getReaderConfig(), true)
                .setReadPositionListener(readPosition -> {
                    // 可在此保存阅读进度
                });

        EventBus.getDefault().register(this);

        folioReader.openBook(filePath); // 可传 asset epub 或 storage 路径
    }

    private Config getReaderConfig() {
        return new Config()
                .setNightMode(false)
                .setFont("LORA")
                .setFontSize(2)
                .setThemeColorRes(R.color.colorPrimary)
                .setShowTts(true)  // 显示 TTS 按钮
                .setAllowedDirection(Config.AllowedDirection.VERTICAL_AND_HORIZONTAL)
                .setDirection(Config.Direction.VERTICAL);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void saveHighlightEvent(SaveHighlightEvent event) {
        HighlightImpl highlight = event.getHighlight();
        // TODO: 保存 highlight 或上传
    }

    @Override
    protected void onDestroy() {
        if (folioReader != null) {
            folioReader.close();
        }
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }*/
}
