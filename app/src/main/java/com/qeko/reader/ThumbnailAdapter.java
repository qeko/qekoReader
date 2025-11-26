package com.qeko.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class ThumbnailAdapter extends RecyclerView.Adapter<ThumbnailAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private final List<File> items;
    private final OnItemClickListener listener;
    private final Context context;
    private int selectedIndex = -1;

    public ThumbnailAdapter(Context ctx, List<File> items, OnItemClickListener listener) {
        this.context = ctx;
        this.items = (items != null) ? items : new ArrayList<>();
        this.listener = listener;
    }

    public void updateItems(List<File> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        selectedIndex = -1;
        notifyDataSetChanged();
    }

    public void setSelectedIndex(int idx) {
        selectedIndex = idx;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ThumbnailAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_thumbnail, parent, false);
        return new ViewHolder(v);
    }


    @Override
    public void onBindViewHolder(@NonNull ThumbnailAdapter.ViewHolder holder, int position) {
        File f = items.get(position);
        holder.itemView.setOnClickListener(v -> {
            selectedIndex = position;
            notifyDataSetChanged();
            if (listener != null) listener.onItemClick(position);
        });

        // 显示选择效果
        holder.selectionOverlay.setVisibility(position == selectedIndex ? View.VISIBLE : View.GONE);

        // 异步加载缩略图，避免阻塞 UI
        holder.bindThumbnail(f);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        View selectionOverlay;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.ivThumb);
            selectionOverlay = itemView.findViewById(R.id.selectionOverlay);
        }

        void bindThumbnail(File file) {
            // cancel previous tasks if any by overwriting tag
            ivThumb.setImageDrawable(null);
            new LoadThumbTask(ivThumb).execute(file.getAbsolutePath());
        }
    }

    // 简单的缩略图异步加载（不使用第三方库）
    private static class LoadThumbTask extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> viewRef;
        private String path;

        LoadThumbTask(ImageView iv) {
            viewRef = new WeakReference<>(iv);
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            path = params[0];
            return decodeSampledBitmapFromFile(path, 160, 160);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            ImageView iv = viewRef.get();
            if (iv != null && bitmap != null) {
                iv.setImageBitmap(bitmap);
            }
        }

        // 根据需要采样，防止OOM
        private static Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, options);

                int height = options.outHeight;
                int width = options.outWidth;
                int inSampleSize = 1;

                if (height > reqHeight || width > reqWidth) {
                    final int halfHeight = height / 2;
                    final int halfWidth = width / 2;

                    while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                        inSampleSize *= 2;
                    }
                }

                options.inSampleSize = inSampleSize;
                options.inJustDecodeBounds = false;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeFile(path, options);
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
