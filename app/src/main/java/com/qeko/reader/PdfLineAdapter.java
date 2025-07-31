package com.qeko.reader;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PdfLineAdapter extends RecyclerView.Adapter<PdfLineAdapter.ViewHolder> {

    private List<String> lines;
    private int highlightIndex = -1;

    public PdfLineAdapter(List<String> lines) {
        this.lines = lines;
    }

    public void setHighlightIndex(int index) {
        highlightIndex = index;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PdfLineAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PdfLineAdapter.ViewHolder holder, int position) {
        holder.textView.setText(lines.get(position));
        if (position == highlightIndex) {
            holder.textView.setBackgroundColor(Color.YELLOW);
        } else {
            holder.textView.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(View v) {
            super(v);
            textView = v.findViewById(android.R.id.text1);
        }
    }
}
