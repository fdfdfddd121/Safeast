package com.app.safeast.objects;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.safeast.R;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private final List<String> messages;

    // Constructor to get the data
    public ChatAdapter(List<String> messages) {
        this.messages = messages;
    }

    // This class holds the views for a single item (our TextView)
    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.messageTextView);
        }
    }

    // Called when RecyclerView needs a new "row" to display.
    // We inflate our layout here.
    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new MessageViewHolder(view);
    }

    // Called for each row to bind the data to the views.
    // This is where we set the text and alignment.
    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        String message = messages.get(position);
        holder.messageTextView.setText(message);

        // --- Logic to align user vs. bot messages ---
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.messageTextView.getLayoutParams();
        if (message.startsWith("You:")) {
            params.gravity = android.view.Gravity.END;
            holder.messageTextView.setBackgroundResource(R.drawable.rounded_corner_chat_user); // A different color for user
        } else {
            params.gravity = android.view.Gravity.START;
            holder.messageTextView.setBackgroundResource(R.drawable.rounded_corner_chat); // Blue for bot
        }
        holder.messageTextView.setLayoutParams(params);
    }

    // Tells the RecyclerView how many items are in the list.
    @Override
    public int getItemCount() {
        return messages.size();
    }
}
