package com.qeko.tts;


import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;


public class TextToSpeechManager {
    private TextToSpeech tts = null;
    private Runnable onDoneCallback;

    public TextToSpeechManager(Context context,  Runnable onDone) {
        this.onDoneCallback = onDone;
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.CHINESE);
               }
        });

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) {
                if (onDoneCallback != null) {
                    new Handler(Looper.getMainLooper()).post(onDoneCallback);
                }
            }

            @Override public void onError(String utteranceId) {}
        });
    }

/*    public void getSpeedStatus() {
        return tts.
    }*/

    public void setSpeed(float speed) {

        tts.setSpeechRate(speed);
    }

    public boolean isSpeaking() {

        return tts.isSpeaking();
    }

    public void speak(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString());
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utterance");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
    }
    /*
    public void speak(String text, Runnable onDone) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String utteranceId = UUID.randomUUID().toString();
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onError(String id) {}
                @Override
                public void onDone(String id) {
                    new Handler(Looper.getMainLooper()).post(onDone);
                }
            });
        }
    }*/

    public void stop() {
        tts.stop();
    }

    public void shutdown() {
        tts.shutdown();
    }
}
