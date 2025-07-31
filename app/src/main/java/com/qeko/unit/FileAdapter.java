package com.qeko.unit;




import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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

        if (item.isLastRead()) {
            holder.title.setTextColor(Color.RED);
            holder.icon.setImageResource(R.drawable.ic_pin);  // 📌图标
        } else {
            holder.title.setTextColor(Color.BLACK);
            holder.icon.setImageResource(item.isFolder()
                    ? (item.isExpanded() ? R.drawable.ic_folder_open : R.drawable.ic_folder_closed)
                    : R.drawable.ic_file);
        }


        // ✅ 设置隔行背景色
        int backgroundColor = (position % 2 == 0) ? Color.parseColor("#FFFFFF") : Color.parseColor("#F5F5F5");
        holder.itemView.setBackgroundColor(backgroundColor);

        // ✅ 选中项高亮
        if (item.isLastRead()) {
            holder.itemView.setBackgroundColor(Color.parseColor("#D0F0C0")); // 浅绿色
        }
        
/*        if (item.isLastRead()) {
            holder.icon.setVisibility(View.VISIBLE);
            holder.title.setTextColor(Color.RED);
        } else {
            holder.icon.setVisibility(View.GONE);
            holder.title.setTextColor(Color.BLACK);
        }*/

    }


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
