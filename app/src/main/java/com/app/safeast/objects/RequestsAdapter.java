package com.app.safeast.objects;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.safeast.R;

import java.util.List;

public class RequestsAdapter
        extends RecyclerView.Adapter<RequestsAdapter.RequestVH> {

    public interface ActionListener {
        void onApprove(Request r);
        void onDecline(Request r);
    }

    private List<Request> list;
    private ActionListener listener;

    public RequestsAdapter(List<Request> list, ActionListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RequestVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_request, parent, false);
        return new RequestVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestVH h, int pos) {
        Request r = list.get(pos);
        h.usernameTV.setText(r.fromUsername);

        if (r.type.equals("FRIEND")) {
            h.approveBTN.setText("Accept");
        } else if (r.type.equals("GPS")) {
            h.approveBTN.setText("Share GPS (1 min)");
        }

        h.approveBTN.setOnClickListener(v -> listener.onApprove(r));
        h.declineBTN.setOnClickListener(v -> listener.onDecline(r));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class RequestVH extends RecyclerView.ViewHolder {
        TextView usernameTV;
        Button approveBTN, declineBTN;

        RequestVH(View v) {
            super(v);
            usernameTV = v.findViewById(R.id.reqUsernameTV);
            approveBTN = v.findViewById(R.id.approveBTN);
            declineBTN = v.findViewById(R.id.declineBTN);
        }
    }
}
