package com.qeko.utils;




import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.qeko.reader.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(FileItem item);
    }

    private List<FileItem> allItems = new ArrayList<>();
    private List<FileItem> visibleItems = new ArrayList<>();
    private OnItemClickListener listener;

    public FileAdapter(List<FileItem> items) {
        this.allItems = items;
        refreshDisplayItems(); // 初始构建 visibleItems
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    public void refreshDisplayItems() {
        visibleItems.clear();
        for (FileItem item : allItems) {
            visibleItems.add(item);
            if (item.isFolder() && item.isExpanded()) {
                visibleItems.addAll(item.getChildren());
            }
        }
        notifyDataSetChanged();
    }

    /** MainActivity中调用，用于更新列表数据 **/
    public void setData(List<FileItem> newItems) {
        visibleItems.clear();
        if (newItems != null) {
            visibleItems.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public FileItem getItemAt(int position) {
        return visibleItems.get(position);
    }

    @NonNull
    @Override
    public FileAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }


    @Override
    public void onBindViewHolder(@NonNull FileAdapter.ViewHolder holder, int position) {
        FileItem item = visibleItems.get(position);
        holder.bind(item, listener);

        // 设置隔行背景色
        int backgroundColor = (position % 2 == 0) ? Color.parseColor("#FFFFFF") : Color.parseColor("#F5F5F5");
        holder.itemView.setBackgroundColor(backgroundColor);

        // 选中项高亮
        if (item.isLastRead()) {
            holder.itemView.setBackgroundColor(Color.parseColor("#D0F0C0")); // 浅绿色
        }

        // ===== 显示文件名 =====
        holder.title.setText(item.getFile().getName());

        // ===== 置顶文件 =====
        if (item.isPinned()) {
            holder.title.setTextColor(Color.RED);
//            holder.icon.setImageResource(R.drawable.ic_pin);
            return; // 置顶的就不再判断类型，直接结束
        } else {
            holder.title.setTextColor(Color.BLACK);
        }

        // ===== 文件夹处理 =====
        if (item.isFolder()) {
            holder.icon.setImageResource(R.drawable.ic_folder_closed);
            return;
        }

        // ===== 图片文件处理（缩略图） =====
        String name = item.getFile().getName().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp")) {
            Glide.with(holder.itemView.getContext())
                    .load(item.getFile())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(holder.icon);
            return;
        }

        // ===== 其他文件类型图标 =====
        if (name.endsWith(".txt")) {
            holder.icon.setImageResource(R.drawable.ic_file);
        } else if (name.endsWith(".pdf")) {
            holder.icon.setImageResource(R.drawable.ic_pdf);
        } else if (name.endsWith(".epub")) {
            holder.icon.setImageResource(R.drawable.ic_epub);
        } else if (name.endsWith(".mobi") || name.endsWith(".azw") || name.endsWith(".azw3")) {
            holder.icon.setImageResource(R.drawable.ic_ebook);
        } else if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac")) {
            holder.icon.setImageResource(R.drawable.ic_music);
        } else if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi")) {
            holder.icon.setImageResource(R.drawable.ic_video);
        } else {
            holder.icon.setImageResource(R.drawable.ic_file); // 默认
        }

        // ===== 点击事件 =====
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    /*@Override
    public void onBindViewHolder(@NonNull FileAdapter.ViewHolder holder, int position) {
        FileItem item = visibleItems.get(position);
        holder.bind(item, listener);

        if (item.isPinned()) {
            holder.title.setTextColor(Color.RED);
            holder.icon.setImageResource(R.drawable.ic_pin);  // 📌图标  这行没生效，问题不大
        } else {
            if (item.isPinned()) {
                holder.icon.setImageResource(R.drawable.ic_pin); // 置顶图标
                holder.title.setTextColor(Color.RED);
            } else if (item.isImage()) {
                // 图片文件 → 显示缩略图

                Glide.with(holder.itemView.getContext())
                        .load(item.getFile())
                        .placeholder(R.drawable.ic_image_placeholder)
                        .centerCrop()
                        .into(holder.icon);
                holder.title.setTextColor(Color.BLACK);
            } else {
                holder.icon.setImageResource(R.drawable.ic_file);
                holder.title.setTextColor(Color.BLACK);
            }
        }
*//*        } else {
            holder.title.setTextColor(Color.BLACK);
            holder.icon.setImageResource(item.isFolder()
                    ? (item.isExpanded() ? R.drawable.ic_folder_open : R.drawable.ic_folder_closed)
                    : R.drawable.ic_file);
        }*//*


        // ✅ 设置隔行背景色
        int backgroundColor = (position % 2 == 0) ? Color.parseColor("#FFFFFF") : Color.parseColor("#F5F5F5");
        holder.itemView.setBackgroundColor(backgroundColor);

        // ✅ 选中项高亮
        if (item.isLastRead()) {
            holder.itemView.setBackgroundColor(Color.parseColor("#D0F0C0")); // 浅绿色
        }

        if (item.isFolder()) {
            holder.icon.setImageResource(R.drawable.ic_folder_closed);
        } else {
            String name = item.getFile().getName().toLowerCase();

            if (name.endsWith(".txt")) {
                holder.icon.setImageResource(R.drawable.ic_file);
            } else if (name.endsWith(".pdf")) {
                holder.icon.setImageResource(R.drawable.ic_pdf);
            } else if (name.endsWith(".epub")) {
                holder.icon.setImageResource(R.drawable.ic_epub);
            } else if (name.endsWith(".mobi") || name.endsWith(".azw") || name.endsWith(".azw3")) {
                holder.icon.setImageResource(R.drawable.ic_ebook);
            } else if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac")) {
                holder.icon.setImageResource(R.drawable.ic_music);
            } else if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi")) {
                holder.icon.setImageResource(R.drawable.ic_video);
            } else if (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".gif")) {
                holder.icon.setImageResource(R.drawable.ic_image);
            } else {
                holder.icon.setImageResource(R.drawable.ic_file); // 默认
            }
        }

        holder.title.setText(item.getFile().getName());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });

    }*/


    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title;
        TextView extra;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            title = itemView.findViewById(R.id.title);
            extra = itemView.findViewById(R.id.extraInfo);
        }

        void bind(FileItem item, OnItemClickListener listener) {
            File file = item.getFile();
            title.setText(file.getName());

            if (item.isFolder()) {
                icon.setImageResource(item.isExpanded() ? R.drawable.ic_folder_open : R.drawable.ic_folder_closed);
                extra.setText(item.getDocumentCount() + " 个文档");
            } else {
                icon.setImageResource(R.drawable.ic_file);
                extra.setText(formatFileSize(file.length()));
            }

            if (item.isLastRead()) {
                title.setTextColor(Color.RED);
            } else {
                title.setTextColor(Color.BLACK);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }

        private String formatFileSize(long size) {
            if (size >= 1024 * 1024) return String.format("%.1f MB", size / 1024f / 1024f);
            else if (size >= 1024) return String.format("%.1f KB", size / 1024f);
            else return size + " B";
        }
    }

    public void setItems(List<FileItem> items) {
        allItems.clear();
        allItems.addAll(items);
        refreshDisplayItems();
    }
}
