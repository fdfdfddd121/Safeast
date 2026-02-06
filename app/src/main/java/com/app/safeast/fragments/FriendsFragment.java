package com.app.safeast.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.safeast.R;
import com.app.safeast.activities.LoginActivity;
import com.app.safeast.activities.MainActivity;
import com.app.safeast.managers.FriendsManager;
import com.app.safeast.objects.Friend;
import com.app.safeast.objects.FriendsAdapter;
import com.app.safeast.objects.GPSShare;
import com.app.safeast.objects.Request;
import com.app.safeast.objects.RequestsAdapter;
import com.app.safeast.objects.SearchUserAdapter;
import com.app.safeast.objects.SharedLocationViewModel;
import com.app.safeast.objects.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FriendsFragment extends Fragment implements FriendsManager.Callbacks {

    Button loginORSignBTN, logoutBTN;
    TextView usernameTV;
    LinearLayout friendsMenu;
    RecyclerView requestsRV, searchRV, friendsRV;
    SearchView searchFriends;

    FriendsManager fm;
    SharedLocationViewModel sharedViewModel;

    SearchUserAdapter searchAdapter;
    RequestsAdapter requestsAdapter;
    FriendsAdapter friendsAdapter;

    final List<User> allUsers = new ArrayList<>();
    final List<Friend> friendsList = new ArrayList<>();
    final List<Request> requestList = new ArrayList<>();

    final Set<String> friendUids = new HashSet<>();
    final Set<String> pendingRequestUids = new HashSet<>();
    final Set<String> pendingGpsUids = new HashSet<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friends, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize ViewModel
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedLocationViewModel.class);

        loginORSignBTN = view.findViewById(R.id.loginORSignBTN);
        logoutBTN = view.findViewById(R.id.logoutBTN);
        usernameTV = view.findViewById(R.id.greetUser);
        friendsMenu = view.findViewById(R.id.friendsMenu);
        requestsRV = view.findViewById(R.id.requestList);
        searchRV = view.findViewById(R.id.searchList);
        friendsRV = view.findViewById(R.id.friendsList);
        searchFriends = view.findViewById(R.id.searchView);

        requestsAdapter = new RequestsAdapter(requestList, new RequestsAdapter.ActionListener() {
            @Override public void onApprove(Request r) {
                if ("FRIEND".equals(r.type)) {
                    fm.approveFriend(r);
                } else if ("GPS".equals(r.type)) {
                    fm.approveGpsRequest(r);
                }
            }
            @Override public void onDecline(Request r) {
                fm.declineRequest(r);
            }
        });

        friendsAdapter = new FriendsAdapter(friendsList, new FriendsAdapter.FriendActionListener() {
            @Override
            public void onGpsRequest(Friend f) {
                // Get username from database instead of getDisplayName()
                FirebaseDatabase.getInstance()
                        .getReference("Users")
                        .child(MainActivity.currentUser.getUid())
                        .child("username")
                        .get()
                        .addOnSuccessListener(snap -> {
                            String username = snap.getValue(String.class);
                            if (username == null) username = "Unknown";
                            fm.sendGpsRequest(f.uid, username);
                        });
            }

            @Override
            public void onRemoveFriend(Friend f) {
                fm.removeFriend(f.uid);
            }
        });

        searchAdapter = new SearchUserAdapter(
                allUsers,
                MainActivity.currentUser != null ? MainActivity.currentUser.getUid() : ""
        );

        requestsRV.setLayoutManager(new LinearLayoutManager(requireContext()));
        friendsRV.setLayoutManager(new LinearLayoutManager(requireContext()));
        searchRV.setLayoutManager(new LinearLayoutManager(requireContext()));

        requestsRV.setAdapter(requestsAdapter);
        friendsRV.setAdapter(friendsAdapter);
        searchRV.setAdapter(searchAdapter);

        searchFriends.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { filterUsers(q); return true; }
            @Override public boolean onQueryTextChange(String t) { filterUsers(t); return true; }
        });

        loginORSignBTN.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), LoginActivity.class));
        });

        logoutBTN.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            MainActivity.currentUser = null;
            updateUI();
        });

        updateUI();
    }

    public void updateUI() {
        if (MainActivity.currentUser == null) {
            loginORSignBTN.setVisibility(View.GONE);
            friendsMenu.setVisibility(View.VISIBLE);
            return;
        }

        loginORSignBTN.setVisibility(View.GONE);
        friendsMenu.setVisibility(View.VISIBLE);

        // Fetch username from Firebase Database instead of using getDisplayName()
        FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(MainActivity.currentUser.getUid())
                .child("username")
                .get()
                .addOnSuccessListener(snap -> {
                    String username = snap.getValue(String.class);
                    if (username != null) {
                        usernameTV.setText("Hello " + username + "!");
                    } else {
                        usernameTV.setText("Hello User!");
                    }
                })
                .addOnFailureListener(e -> {
                    usernameTV.setText("Hello User!");
                });

        if (fm == null) {
            fm = new FriendsManager(MainActivity.currentUser.getUid(), requireContext(), this);
            fm.start();
        }
    }

    private void filterUsers(String text) {
        List<User> filtered = new ArrayList<>();

        if (text.isEmpty()) {
            filtered.addAll(allUsers);
        } else {
            for (User u : allUsers) {
                if (u.username.toLowerCase().contains(text.toLowerCase())) {
                    filtered.add(u);
                }
            }
        }

        searchAdapter = new SearchUserAdapter(filtered, MainActivity.currentUser.getUid());
        searchAdapter.setPendingRequests(pendingRequestUids);
        searchAdapter.setFriends(friendUids);
        searchRV.setAdapter(searchAdapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (fm != null) fm.stop();
    }

    /* ===================== MANAGER CALLBACKS ===================== */

    @Override
    public void onUsersUpdated(List<User> users) {
        allUsers.clear();
        allUsers.addAll(users);
        searchAdapter.notifyDataSetChanged();
    }

    @Override
    public void onFriendsUpdated(List<Friend> friends) {
        friendsList.clear();
        friendsList.addAll(friends);

        friendUids.clear();
        for (Friend f : friends) friendUids.add(f.uid);

        friendsAdapter.notifyDataSetChanged();
    }

    @Override
    public void onRequestsUpdated(List<Request> requests) {
        Log.d("FriendsFragment", "========== REQUESTS UPDATED ==========");
        Log.d("FriendsFragment", "Total requests: " + requests.size());

        requestList.clear();
        requestList.addAll(requests);

        requestsAdapter.notifyDataSetChanged();
    }

    // ADD THIS NEW CALLBACK
    @Override
    public void onPendingFriendRequestsUpdated(Set<String> pending) {
        Log.d("FriendsFragment", "========== PENDING FRIEND REQUESTS UPDATED ==========");
        Log.d("FriendsFragment", "Pending count: " + pending.size());
        Log.d("FriendsFragment", "Pending UIDs: " + pending);

        pendingRequestUids.clear();
        pendingRequestUids.addAll(pending);

        // UPDATE SEARCH ADAPTER WITH PENDING STATE
        searchAdapter.setPendingRequests(new HashSet<>(pendingRequestUids));
    }

    @Override
    public void onPendingGpsUpdated(Set<String> pending) {
        pendingGpsUids.clear();
        pendingGpsUids.addAll(pending);
        friendsAdapter.setPendingGpsRequests(pendingGpsUids);
    }

    @Override
    public void onGpsShareReceived(GPSShare share, boolean isNew) {
        // Push to ViewModel for MapFragment
        sharedViewModel.updateFriendLocation(share.fromUid, share);

        // Auto-switch to map if this is a NEW share
        if (isNew && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).switchToMapTab();

            String name = share.fromUsername != null ? share.fromUsername : "Friend";
            Toast.makeText(requireContext(),
                    "📍 " + name + " shared location! Opening map...",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onGpsShareExpired(String fromUid) {
        // Remove from ViewModel
        sharedViewModel.removeFriendLocation(fromUid);
    }

    @Override
    public void onGpsApprovalSuccess(String toUsername) {
        Toast.makeText(requireContext(),
                "📍 Location shared with " + toUsername + " for 1 minute",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onGpsApprovalFailed(String reason) {
        Toast.makeText(requireContext(),
                "⚠️ " + reason,
                Toast.LENGTH_LONG).show();
    }
}