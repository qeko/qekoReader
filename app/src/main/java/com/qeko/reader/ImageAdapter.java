package com.qeko.reader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    private Context context;
    private List<File> files;
    private boolean isGridMode;

    public ImageAdapter(Context context, List<File> files, boolean isGridMode) {
        this.context = context;
        this.files = files;
        this.isGridMode = isGridMode;
    }

    public void setGridMode(boolean isGridMode) {
        this.isGridMode = isGridMode;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        File file = files.get(position);

        // 加载缩略图（示例用Glide，你可以换成你项目的图片加载库）
        int size = isGridMode ? 200 : 100; // 网格模式显示大图，列表模式显示小图
        Glide.with(context)
                .load(file)
                .override(size, size)
                .centerCrop()
                .into(holder.ivThumbnail);

        // 显示文件名（列表模式下显示，网格模式可隐藏）
        holder.tvName.setVisibility(isGridMode ? View.GONE : View.VISIBLE);
        holder.tvName.setText(file.getName());
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;
        TextView tvName;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.ivThumbnail);
            tvName = itemView.findViewById(R.id.tvName);
        }
    }
}
