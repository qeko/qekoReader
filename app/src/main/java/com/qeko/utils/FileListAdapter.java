package com.qeko.utils;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.qeko.reader.R;

import java.io.File;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.ViewHolder> {

    private final List<File> fileList;
    private final Context context;
    private final OnFileClickListener listener;

    // 用于避免重复显示同一目录
    private final Map<String, Integer> folderCountMap = new HashMap<>();
    private final Map<String, Boolean> folderShownMap = new HashMap<>();

    public interface OnFileClickListener {
        void onFileClick(File file);
        void onFileDeleted(File file, int position);
    }

    public FileListAdapter(Context context, List<File> fileList, OnFileClickListener listener) {
        this.context = context;
        this.fileList = fileList;
        this.listener = listener;
        computeFolderFileCounts();
    }

    private void computeFolderFileCounts() {
        folderCountMap.clear();
        for (File file : fileList) {
            String parent = file.getParentFile().getName();
            folderCountMap.put(parent, folderCountMap.getOrDefault(parent, 0) + 1);
        }
    }
/*
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvFolder;
        TextView tvFile;

        public ViewHolder(View itemView) {
            super(itemView);
//            tvFolder = itemView.findViewById(R.id.tvFolder);
            tvFile = itemView.findViewById(R.id.tvFile);
        }
    }*/

    public static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView ivIcon;
        TextView tvName;
        TextView tvFolder;
        TextView tvFile;

        public ViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvName = itemView.findViewById(R.id.tvName);
//            tvFolder = itemView.findViewById(R.id.tvFolder);
//            tvFile = itemView.findViewById(R.id.tvFile);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        File file = fileList.get(position);
        String fileName = file.getName();
        long size = file.length();
        String sizeStr = formatFileSize(size);

        String parentName = file.getParentFile().getName();
        int count = folderCountMap.getOrDefault(parentName, 1);

        // 显示文件夹信息（只在首次出现时）
        if (!folderShownMap.containsKey(parentName)) {
            folderShownMap.put(parentName, true);
            holder.tvFolder.setVisibility(View.VISIBLE);
            holder.tvFolder.setText("📁 " + parentName + " (" + count + "个文件)");
        } else {
            holder.tvFolder.setVisibility(View.GONE);
        }

        holder.tvFile.setText(fileName + " (" + sizeStr + ")");
        holder.tvFile.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFileClick(file);
            }
        });
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    public void deleteItem(int position) {
        File file = fileList.get(position);
        if (file.delete()) {
            fileList.remove(position);
            notifyItemRemoved(position);
            Toast.makeText(context, "已删除：" + file.getName(), Toast.LENGTH_SHORT).show();
            if (listener != null) {
                listener.onFileDeleted(file, position);
            }
        } else {
            Toast.makeText(context, "删除失败：" + file.getName(), Toast.LENGTH_SHORT).show();
        }

        // 更新文件夹显示状态
        folderShownMap.clear();
        computeFolderFileCounts();
        notifyDataSetChanged();
    }

    private String formatFileSize(long bytes) {
        DecimalFormat df = new DecimalFormat("#.##");
        if (bytes >= 1024 * 1024) {
            return df.format(bytes / (1024.0 * 1024.0)) + " M";
        } else {
            return df.format(bytes / 1024.0) + " K";
        }
    }
}
