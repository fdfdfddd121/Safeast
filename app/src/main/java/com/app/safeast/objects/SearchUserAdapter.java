package com.app.safeast.objects;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.safeast.R;
import com.app.safeast.activities.MainActivity;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SearchUserAdapter extends RecyclerView.Adapter<SearchUserAdapter.UserViewHolder> {

    private List<User> users;
    private String currentUserId;
    private Set<String> pendingRequestUids = new HashSet<>();  // Track pending requests
    private Set<String> friendUids = new HashSet<>();  // Track existing friends

    public SearchUserAdapter(List<User> users, String currentUserId) {
        this.users = users;
        this.currentUserId = currentUserId;
    }

    // Add method to update friends list
    public void setFriends(Set<String> uids) {
        this.friendUids = uids;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_search, parent, false);
        return new UserViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = users.get(position);
        holder.usernameTV.setText(user.username);

        boolean isPending = pendingRequestUids.contains(user.uid);
        boolean isFriend = friendUids.contains(user.uid);

        Log.d("SearchUserAdapter", "Binding user: " + user.username +
                " (UID: " + user.uid + "), isPending=" + isPending + ", isFriend=" + isFriend);

        if (isFriend) {
            // Already friends - show as friend
            holder.addFriendBTN.setText("Already Friends");
            holder.addFriendBTN.setEnabled(false);
            holder.itemView.setAlpha(0.5f);
            holder.addFriendBTN.setOnClickListener(null);
        } else if (isPending) {
            // Request pending
            holder.addFriendBTN.setText("Pending...");
            holder.addFriendBTN.setEnabled(false);
            holder.itemView.setAlpha(0.5f);
            holder.addFriendBTN.setOnClickListener(null);
            Log.d("SearchUserAdapter", "🟡 Set " + user.username + " to PENDING state");
        } else {
            // Can send request
            holder.addFriendBTN.setText("Add Friend");
            holder.addFriendBTN.setEnabled(true);
            holder.itemView.setAlpha(1.0f);
            holder.addFriendBTN.setOnClickListener(v -> {
                Log.d("SearchUserAdapter", "📤 Sending friend request to: " + user.username);
                sendFriendRequest(user);
                // Immediately mark as pending locally
                pendingRequestUids.add(user.uid);
                notifyItemChanged(position);
            });
        }
    }

    // Add this method to update pending requests
    public void setPendingRequests(Set<String> uids) {
        Log.d("SearchUserAdapter", "📥 Updating pending requests. Count: " + uids.size());
        Log.d("SearchUserAdapter", "📥 Pending UIDs: " + uids.toString());
        this.pendingRequestUids.clear();
        this.pendingRequestUids.addAll(uids);
        notifyDataSetChanged();
    }

    private void sendFriendRequest(User toUser) {
        if (MainActivity.currentUser == null) return;

        String myUid = MainActivity.currentUser.getUid();
        String combinedKey = getCombinedRequestKey(myUid, toUser.uid);

        // Get the sender's username from Firebase first
        FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(myUid)
                .child("username")
                .get()
                .addOnSuccessListener(snap -> {
                    String myUsername = snap.getValue(String.class);
                    if (myUsername == null) myUsername = "Unknown";

                    // Use new FriendRequests structure
                    DatabaseReference reqRef = FirebaseDatabase.getInstance()
                            .getReference("FriendRequests")
                            .child(combinedKey);

                    Map<String, Object> data = new HashMap<>();
                    data.put("fromUid", myUid);
                    data.put("toUid", toUser.uid);
                    data.put("fromUsername", myUsername);
                    data.put("type", "FRIEND");
                    data.put("status", "PENDING");
                    data.put("timestamp", System.currentTimeMillis());

                    reqRef.setValue(data);
                    Log.d("SearchUserAdapter", "✅ Friend request created at: FriendRequests/" + combinedKey);
                });
    }

    // Add helper method to adapter
    private String getCombinedRequestKey(String uid1, String uid2) {
        if (uid1.compareTo(uid2) < 0) {
            return uid1 + "_" + uid2;
        } else {
            return uid2 + "_" + uid1;
        }
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView usernameTV;
        Button addFriendBTN;

        UserViewHolder(View itemView) {
            super(itemView);
            usernameTV = itemView.findViewById(R.id.usernameTV);
            addFriendBTN = itemView.findViewById(R.id.addFriendBTN);
        }
    }
}