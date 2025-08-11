package com.qeko.reader;
import android.content.Intent;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

public class MusicServiceConnector {
    private static final MusicServiceConnector instance = new MusicServiceConnector();
    private MusicService musicService;
    private boolean isBound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;
            if (onConnectedListener != null) {
                onConnectedListener.onConnected(musicService);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
        }
    };

    public static MusicServiceConnector getInstance() {
        return instance;
    }

    public void bind(Context context, OnConnectedListener listener) {
        this.onConnectedListener = listener;
        Intent intent = new Intent(context, MusicService.class);
        context.startService(intent);
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    public void unbind(Context context) {
        if (isBound) {
            context.unbindService(connection);
            isBound = false;
        }
    }

    public MusicService getService() {
        return musicService;
    }

    public interface OnConnectedListener {
        void onConnected(MusicService service);
    }

    private OnConnectedListener onConnectedListener;
}
