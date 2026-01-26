package com.app.safeast.objects;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.safeast.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.FriendVH> {

    public interface FriendActionListener {
        void onGpsRequest(Friend f);
        void onRemoveFriend(Friend f);
    }

    private List<Friend> list;
    private FriendActionListener listener;
    private Set<String> pendingGpsRequestUids = new HashSet<>();  // Track pending GPS requests

    public FriendsAdapter(List<Friend> list, FriendActionListener listener) {
        this.list = list;
        this.listener = listener;
    }

    // Add method to update pending GPS requests
    public void setPendingGpsRequests(Set<String> uids) {
        this.pendingGpsRequestUids.clear();
        this.pendingGpsRequestUids.addAll(uids);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FriendVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend, parent, false);
        return new FriendVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendVH h, int pos) {
        Friend f = list.get(pos);
        h.usernameTV.setText(f.username);

        boolean hasPendingGps = pendingGpsRequestUids.contains(f.uid);

        if (hasPendingGps) {
            h.gpsBTN.setText("GPS Pending...");
            h.gpsBTN.setEnabled(false);
            h.gpsBTN.setAlpha(0.5f);
        } else {
            h.gpsBTN.setText("Request GPS");
            h.gpsBTN.setEnabled(true);
            h.gpsBTN.setAlpha(1.0f);
            h.gpsBTN.setOnClickListener(v -> listener.onGpsRequest(f));
        }

        h.removeBTN.setOnClickListener(v -> listener.onRemoveFriend(f));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class FriendVH extends RecyclerView.ViewHolder {
        TextView usernameTV;
        Button gpsBTN;
        Button removeBTN;

        FriendVH(View v) {
            super(v);
            usernameTV = v.findViewById(R.id.friendUsernameTV);
            gpsBTN = v.findViewById(R.id.gpsBTN);
            removeBTN = v.findViewById(R.id.removeFriendBTN);
        }
    }
}