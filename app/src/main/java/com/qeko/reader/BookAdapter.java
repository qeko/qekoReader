package com.qeko.reader;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;


    public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {
        private List<Book> books;
        private BookClickListener listener;

        public BookAdapter(List<Book> books, BookClickListener listener) {
            this.books = books;
            this.listener = listener;
        }

        @NonNull
        @Override
        public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_book, parent, false);
            return new BookViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
            Book book = books.get(position);
            holder.title.setText(book.getTitle());
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onBookClick(book);
            });
        }

        @Override
        public int getItemCount() {
            return books.size();
        }

        public interface OnItemClickListener {
        }

        static class BookViewHolder extends RecyclerView.ViewHolder {
            TextView title;

            public BookViewHolder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.bookName);

            }
        }


}
