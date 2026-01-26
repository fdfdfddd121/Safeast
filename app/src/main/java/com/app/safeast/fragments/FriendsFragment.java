package com.app.safeast.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.app.safeast.R;
import com.app.safeast.activities.LoginActivity;
import com.app.safeast.activities.MainActivity;
import com.app.safeast.objects.FriendsAdapter;
import com.app.safeast.objects.GPSShare;
import com.app.safeast.objects.Request;
import com.app.safeast.objects.RequestsAdapter;
import com.app.safeast.objects.SearchUserAdapter;
import com.app.safeast.objects.User;
import com.app.safeast.objects.Friend;
import androidx.lifecycle.ViewModelProvider;
import com.app.safeast.objects.SharedLocationViewModel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FriendsFragment extends Fragment {

    Button loginORSignBTN;
    Button logoutBTN;
    TextView usernameTV;
    LinearLayout friendsMenu;
    ActivityResultLauncher<Intent> launcher;
    RecyclerView requestsRV;
    RecyclerView searchRV;
    RecyclerView friendsRV;
    SearchView searchFriends;
    private FusedLocationProviderClient fusedLocationClient;
    private SharedLocationViewModel sharedViewModel;



    List<User> allUsers = new ArrayList<>();
    SearchUserAdapter searchAdapter;
    DatabaseReference usersRef;

    private ValueEventListener requestsListener;
    private ValueEventListener friendsListener;
    private ValueEventListener usersValueListener;
    private ValueEventListener sentRequestsListener;

    // Track pending requests and friends
    private Set<String> pendingRequestUids = new HashSet<>();
    private Set<String> friendUids = new HashSet<>();

    // ===== Requests =====
    List<Request> requestList = new ArrayList<>();
    RequestsAdapter requestsAdapter;

    private Set<String> pendingGpsRequestUids = new HashSet<>();
    private ValueEventListener gpsRequestsListener;
    private ValueEventListener gpsSharesListener;

    // ===== Friends =====
    List<Friend> friendsList = new ArrayList<>();
    FriendsAdapter friendsAdapter;

    // ===== Firebase =====
    DatabaseReference requestsRef;
    DatabaseReference friendsRef;

    public FriendsFragment() {
        // Required empty public constructor
    }

    public static FriendsFragment newInstance() {
        return new FriendsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friends, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loginORSignBTN = view.findViewById(R.id.loginORSignBTN);
        logoutBTN = view.findViewById(R.id.logoutBTN);
        usernameTV = view.findViewById(R.id.greetUser);
        friendsMenu = view.findViewById(R.id.friendsMenu);
        requestsRV = view.findViewById(R.id.requestList);
        searchRV = view.findViewById(R.id.searchList);
        friendsRV = view.findViewById(R.id.friendsList);
        searchFriends = view.findViewById(R.id.searchView);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedLocationViewModel.class);

        launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == MainActivity.RESULT_OK && result.getData() != null) {
                        MainActivity.currentUser = result.getData().getParcelableExtra("user");
                        updateUI();
                    }
                });

        requestsAdapter = new RequestsAdapter(requestList, new RequestsAdapter.ActionListener() {
            @Override
            public void onApprove(Request r) {
                if (r.type.equals("FRIEND")) approveFriendRequest(r);
                else approveGpsRequest(r);
            }

            @Override
            public void onDecline(Request r) {
                declineRequest(r);
            }
        });

        friendsAdapter = new FriendsAdapter(friendsList, new FriendsAdapter.FriendActionListener() {
            @Override
            public void onGpsRequest(Friend f) {
                sendGpsRequest(f);
            }

            @Override
            public void onRemoveFriend(Friend f) {
                removeFriend(f);
            }
        });

        requestsRV.setLayoutManager(new LinearLayoutManager(requireContext()));
        requestsRV.setAdapter(requestsAdapter);

        friendsRV.setLayoutManager(new LinearLayoutManager(requireContext()));
        friendsRV.setAdapter(friendsAdapter);

        searchRV.setLayoutManager(new LinearLayoutManager(requireContext()));

        loginORSignBTN.setOnClickListener(this::loginORSignBTNClick);
        logoutBTN.setOnClickListener(this::logoutBTNClick);

        updateUI();
    }

    // Add this helper method at the top of the class
    private String getCombinedRequestKey(String uid1, String uid2) {
        // Always sort alphabetically to ensure consistent key
        if (uid1.compareTo(uid2) < 0) {
            return uid1 + "_" + uid2;
        } else {
            return uid2 + "_" + uid1;
        }
    }

    public void updateUI() {
        if (MainActivity.currentUser != null) {
            loginORSignBTN.setVisibility(View.GONE);
            friendsMenu.setVisibility(View.VISIBLE);
            initSearch();
            loadRequests();
            loadFriends();
            loadSentRequests();
            loadGpsRequests();
            loadGpsShares();

            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference()
                    .child("Users")
                    .child(MainActivity.currentUser.getUid())
                    .child("username");

            userRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    String username = task.getResult().getValue(String.class);
                    String greet = "Hello " + (username != null ? username : "User") + "!";
                    usernameTV.setText(greet);
                } else {
                    usernameTV.setText("Hello Guest!");
                }
            });
        } else {
            loginORSignBTN.setVisibility(View.VISIBLE);
            friendsMenu.setVisibility(View.GONE);
        }
    }

    private void initSearch() {
        searchAdapter = new SearchUserAdapter(allUsers, MainActivity.currentUser.getUid());
        searchRV.setAdapter(searchAdapter);

        usersRef = FirebaseDatabase.getInstance().getReference("Users");

        loadAllUsers();
        setupSearch();
    }

    private void loadAllUsers() {
        if (MainActivity.currentUser == null) return;

        usersValueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allUsers.clear();

                for (DataSnapshot userSnap : snapshot.getChildren()) {
                    String uid = userSnap.getKey();

                    if (uid != null && uid.equals(MainActivity.currentUser.getUid()))
                        continue;

                    // Skip friends
                    if (friendUids.contains(uid))
                        continue;

                    String username = userSnap.child("username").getValue(String.class);
                    if (username != null) {
                        allUsers.add(new User(uid, username));
                    }
                }

                searchAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FriendsFragment", "Error loading users: " + error.getMessage());
            }
        };

        usersRef.addValueEventListener(usersValueListener);
    }

    private void loadSentRequests() {
        if (MainActivity.currentUser == null) return;

        String myUid = MainActivity.currentUser.getUid();
        Log.d("FriendsFragment", "========== LOADING PENDING REQUESTS ==========");

        DatabaseReference friendRequestsRef = FirebaseDatabase.getInstance()
                .getReference("FriendRequests");

        if (sentRequestsListener != null) {
            friendRequestsRef.removeEventListener(sentRequestsListener);
        }

        sentRequestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                pendingRequestUids.clear();

                Log.d("FriendsFragment", "Scanning " + snapshot.getChildrenCount() + " friend requests");

                for (DataSnapshot requestSnap : snapshot.getChildren()) {
                    Request r = requestSnap.getValue(Request.class);

                    if (r != null && r.type != null && r.type.equals("FRIEND")) {
                        // If I'm involved in this request (either sender or receiver)
                        if (myUid.equals(r.fromUid)) {
                            // I sent it to toUid
                            pendingRequestUids.add(r.toUid);
                            Log.d("FriendsFragment", "✉️ I sent request to: " + r.toUid);
                        } else if (myUid.equals(r.toUid)) {
                            // I received it from fromUid
                            pendingRequestUids.add(r.fromUid);
                            Log.d("FriendsFragment", "📬 I received request from: " + r.fromUid);
                        }
                    }
                }

                Log.d("FriendsFragment", "📊 Total pending UIDs: " + pendingRequestUids.size());

                if (searchAdapter != null) {
                    searchAdapter.setPendingRequests(new HashSet<>(pendingRequestUids));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FriendsFragment", "Error loading pending: " + error.getMessage());
            }
        };

        friendRequestsRef.addValueEventListener(sentRequestsListener);
    }

    private void setupSearch() {
        searchFriends.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterUsers(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterUsers(newText);
                return true;
            }
        });
    }

    private void filterUsers(String text) {
        List<User> filtered = new ArrayList<>();

        if (text.isEmpty()) {
            filtered.addAll(allUsers);
        } else {
            for (User user : allUsers) {
                if (user.username.toLowerCase().contains(text.toLowerCase())) {
                    filtered.add(user);
                }
            }
        }

        Log.d("FriendsFragment", "🔍 Filtering users. Query: '" + text + "', Results: " + filtered.size());

        searchAdapter = new SearchUserAdapter(filtered, MainActivity.currentUser.getUid());
        searchAdapter.setPendingRequests(new HashSet<>(pendingRequestUids));
        searchAdapter.setFriends(new HashSet<>(friendUids));
        searchRV.setAdapter(searchAdapter);
    }

    private void loadRequests() {
        if (MainActivity.currentUser == null) {
            Log.d("FriendsFragment", "loadRequests: currentUser is null");
            return;
        }

        String myUid = MainActivity.currentUser.getUid();
        Log.d("FriendsFragment", "Loading requests for UID: " + myUid);

        if (requestsRef != null && requestsListener != null) {
            requestsRef.removeEventListener(requestsListener);
        }

        // Listen to ALL FriendRequests
        requestsRef = FirebaseDatabase.getInstance()
                .getReference("FriendRequests");

        requestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                requestList.clear();

                Log.d("FriendsFragment", "Total FriendRequests in DB: " + snapshot.getChildrenCount());

                for (DataSnapshot snap : snapshot.getChildren()) {
                    Request r = snap.getValue(Request.class);

                    if (r != null && r.type != null && r.type.equals("FRIEND")) {
                        // Only add if I'm the RECIPIENT (toUid)
                        if (myUid.equals(r.toUid)) {
                            r.requestId = snap.getKey();
                            requestList.add(r);
                            Log.d("FriendsFragment", "📬 Request TO me from: " + r.fromUsername);
                        }
                    }
                }

                Log.d("FriendsFragment", "Total requests for me: " + requestList.size());
                requestsAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FriendsFragment", "Error loading requests: " + error.getMessage());
            }
        };

        requestsRef.addValueEventListener(requestsListener);
    }

    private void declineRequest(Request r) {
        if (MainActivity.currentUser == null || r == null || r.requestId == null || r.type == null) {
            Log.e("FriendsFragment", "❌ Cannot decline - invalid data");
            return;
        }

        String node;
        if ("FRIEND".equals(r.type)) {
            node = "FriendRequests";
        } else if ("GPS".equals(r.type)) {
            node = "GPSRequests";
        } else {
            Log.e("FriendsFragment", "❌ Unknown request type: " + r.type);
            return;
        }

        FirebaseDatabase.getInstance()
                .getReference(node)
                .child(r.requestId)
                .removeValue();
    }


    private void approveFriendRequest(Request r) {
        if (MainActivity.currentUser == null || r == null || r.fromUid == null) {
            Log.e("FriendsFragment", "Cannot approve - invalid data");
            return;
        }

        String myUid = MainActivity.currentUser.getUid();
        String otherUid = r.fromUid;

        Log.d("FriendsFragment", "========== APPROVING FRIEND REQUEST ==========");

        DatabaseReference db = FirebaseDatabase.getInstance().getReference();

        // Add friendship both ways
        db.child("Friends").child(myUid).child(otherUid).setValue(true)
                .addOnSuccessListener(aVoid -> {
                    db.child("Friends").child(otherUid).child(myUid).setValue(true)
                            .addOnSuccessListener(aVoid2 -> {
                                // Remove the single request
                                db.child("FriendRequests").child(r.requestId).removeValue()
                                        .addOnSuccessListener(aVoid3 -> {
                                            Log.d("FriendsFragment", "✅ Friend approved successfully!");
                                        });
                            });
                });
    }

    private void loadFriends() {
        if (MainActivity.currentUser == null) return;

        String uid = MainActivity.currentUser.getUid();
        Log.d("FriendsFragment", "Loading friends for UID: " + uid);

        if (friendsRef != null && friendsListener != null) {
            friendsRef.removeEventListener(friendsListener);
        }

        friendsRef = FirebaseDatabase.getInstance()
                .getReference("Friends")
                .child(uid);

        friendsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("FriendsFragment", "Friends snapshot received, count: " + snapshot.getChildrenCount());

                friendsList.clear();
                friendUids.clear();

                for (DataSnapshot snap : snapshot.getChildren()) {
                    String friendUid = snap.getKey();
                    if (friendUid != null) {
                        friendUids.add(friendUid);
                        fetchFriendProfile(friendUid);
                    }
                }

                // Update search adapter with new friends list
                searchAdapter.setFriends(friendUids);
                loadAllUsers();  // Reload to filter out new friends
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FriendsFragment", "Error loading friends: " + error.getMessage());
            }
        };

        friendsRef.addValueEventListener(friendsListener);
    }

    private void fetchFriendProfile(String uid) {
        FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(uid)
                .child("username")
                .get()
                .addOnSuccessListener(snap -> {
                    String username = snap.getValue(String.class);
                    if (username != null) {
                        friendsList.add(new Friend(uid, username));
                        friendsAdapter.notifyDataSetChanged();
                        Log.d("FriendsFragment", "Added friend: " + username);
                    }
                });
    }

    private void removeFriend(Friend friend) {
        if (MainActivity.currentUser == null) return;

        String myUid = MainActivity.currentUser.getUid();
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();

        // Remove from both sides
        db.child("Friends").child(myUid).child(friend.uid).removeValue()
                .addOnSuccessListener(aVoid -> {
                    db.child("Friends").child(friend.uid).child(myUid).removeValue()
                            .addOnSuccessListener(aVoid2 -> {
                                Log.d("FriendsFragment", "Friend removed successfully");
                            });
                });
    }

    private void loadGpsRequests() {
        if (MainActivity.currentUser == null) return;

        String myUid = MainActivity.currentUser.getUid();
        DatabaseReference gpsRequestsRef = FirebaseDatabase.getInstance()
                .getReference("GPSRequests");

        if (gpsRequestsListener != null) {
            gpsRequestsRef.removeEventListener(gpsRequestsListener);
        }

        gpsRequestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                pendingGpsRequestUids.clear();

                // 🔥 REMOVE ALL GPS REQUESTS FIRST (reactive reset)
                requestList.removeIf(r -> "GPS".equals(r.type));

                for (DataSnapshot snap : snapshot.getChildren()) {
                    Request r = snap.getValue(Request.class);
                    if (r == null) continue;

                    r.type = "GPS";               // enforce type
                    r.requestId = snap.getKey();  // enforce id

                    if (myUid.equals(r.fromUid)) {
                        pendingGpsRequestUids.add(r.toUid);
                    }
                    else if (myUid.equals(r.toUid)) {
                        requestList.add(r);
                    }
                }

                friendsAdapter.setPendingGpsRequests(new HashSet<>(pendingGpsRequestUids));
                requestsAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FriendsFragment", "GPS load error: " + error.getMessage());
            }
        };

        gpsRequestsRef.addValueEventListener(gpsRequestsListener);
    }


    // Add this field to FriendsFragment
    private Set<String> knownGpsShares = new HashSet<>();

    // Update loadGpsShares()
    private void loadGpsShares() {
        if (MainActivity.currentUser == null) return;

        String myUid = MainActivity.currentUser.getUid();
        DatabaseReference gpsSharesRef = FirebaseDatabase.getInstance()
                .getReference("GPSShares");

        if (gpsSharesListener != null) {
            gpsSharesRef.removeEventListener(gpsSharesListener);
        }

        gpsSharesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("FriendsFragment", "📍 GPS Shares snapshot. Count: " + snapshot.getChildrenCount());

                boolean foundNewShare = false;
                Set<String> currentShares = new HashSet<>();

                for (DataSnapshot shareSnap : snapshot.getChildren()) {
                    GPSShare share = shareSnap.getValue(GPSShare.class);
                    if (share == null) continue;

                    if (share.isExpired()) {
                        shareSnap.getRef().removeValue();
                        sharedViewModel.removeFriendLocation(share.fromUid);
                        knownGpsShares.remove(share.fromUid);
                        Log.d("FriendsFragment", "🗑️ Removed expired GPS share");
                        continue;
                    }

                    // If this share is for me
                    if (myUid.equals(share.toUid)) {
                        currentShares.add(share.fromUid);

                        // Check if this is a NEW share (not seen before)
                        if (!knownGpsShares.contains(share.fromUid)) {
                            Log.d("FriendsFragment", "🆕 NEW GPS share from: " + share.fromUid);
                            foundNewShare = true;
                        }

                        Log.d("FriendsFragment", "📍 Active GPS share from: " + share.fromUid +
                                " - " + share.getRemainingSeconds() + "s remaining");

                        // PUSH TO VIEWMODEL
                        sharedViewModel.updateFriendLocation(share.fromUid, share);
                    }
                }

                // Update known shares
                knownGpsShares = currentShares;

                // AUTO-SWITCH TO MAP TAB ONLY FOR NEW SHARES
                if (foundNewShare && getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).switchToMapTab();

                    Toast.makeText(requireContext(),
                            "📍 Friend shared location! Opening map...",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FriendsFragment", "Error loading GPS shares: " + error.getMessage());
            }
        };

        gpsSharesRef.addValueEventListener(gpsSharesListener);
    }

    private boolean isLocationEnabled() {
        android.location.LocationManager lm =
                (android.location.LocationManager) requireContext()
                        .getSystemService(android.content.Context.LOCATION_SERVICE);

        return lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
    }


    private void approveGpsRequest(Request r) {
        if (MainActivity.currentUser == null) return;

        String myUid = MainActivity.currentUser.getUid();

        Log.d("FriendsFragment", "========== APPROVING GPS REQUEST ==========");

        // Check location permission
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(requireContext(),
                    "Location permission required",
                    Toast.LENGTH_LONG).show();

            // Remove request
            FirebaseDatabase.getInstance()
                    .getReference("GPSRequests")
                    .child(r.requestId)
                    .removeValue();

            return;
        }

        if (!isLocationEnabled()) {
            Toast.makeText(requireContext(),
                    "Please enable GPS",
                    Toast.LENGTH_LONG).show();

            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }

        // Show loading message
        Toast.makeText(requireContext(), "Getting your location...", Toast.LENGTH_SHORT).show();

        // Use getCurrentLocation instead of getLastLocation
        // This forces a fresh location reading
        fusedLocationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                new com.google.android.gms.tasks.CancellationTokenSource().getToken()
        ).addOnSuccessListener(location -> {
            if (location != null) {
                double lat = location.getLatitude();
                double lng = location.getLongitude();

                Log.d("FriendsFragment", "📍 Current location: " + lat + ", " + lng);

                String combinedKey = getCombinedRequestKey(myUid, r.fromUid);
                DatabaseReference db = FirebaseDatabase.getInstance().getReference();

                db.child("GPSShares").child(combinedKey).get()
                        .addOnSuccessListener(snap -> {
                            if (snap.exists()) {
                                GPSShare existing = snap.getValue(GPSShare.class);
                                if (existing != null && !existing.isExpired()) {
                                    Toast.makeText(requireContext(),
                                            "Location already shared",
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                            }

                            // Get my username
                            db.child("Users").child(myUid).child("username").get()
                                    .addOnSuccessListener(snap2 -> {
                                        String myUsername = snap2.getValue(String.class);
                                        if (myUsername == null) myUsername = "Friend";

                                        // Create GPS share
                                        Map<String, Object> shareData = new HashMap<>();
                                        shareData.put("fromUid", myUid);
                                        shareData.put("toUid", r.fromUid);
                                        shareData.put("fromUsername", myUsername);
                                        shareData.put("latitude", lat);
                                        shareData.put("longitude", lng);
                                        shareData.put("sharedAt", System.currentTimeMillis());
                                        shareData.put("expiresAt", System.currentTimeMillis() + 60000);

                                        db.child("GPSShares").child(combinedKey).setValue(shareData)
                                                .addOnSuccessListener(aVoid -> {
                                                    Log.d("FriendsFragment", "✅ GPS location shared!");

                                                    // Remove the request
                                                    db.child("GPSRequests").child(r.requestId).removeValue()
                                                            .addOnSuccessListener(aVoid2 -> {
                                                                Log.d("FriendsFragment", "✅ Request removed");
                                                                Toast.makeText(requireContext(),
                                                                        "📍 Location shared with " + r.fromUsername + " for 1 minute",
                                                                        Toast.LENGTH_LONG).show();
                                                            });
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e("FriendsFragment", "❌ Share failed: " + e.getMessage());
                                                    Toast.makeText(requireContext(),
                                                            "Failed to share location",
                                                            Toast.LENGTH_SHORT).show();

                                                    // Remove request anyway
                                                    db.child("GPSRequests").child(r.requestId).removeValue();
                                                });
                                    });
                        });
            } else {
                // Still null - GPS is definitely off
                Log.e("FriendsFragment", "❌ Location still null after getCurrentLocation");

                Toast.makeText(requireContext(),
                        "⚠️ Please enable GPS/Location services in your device settings",
                        Toast.LENGTH_LONG).show();

                // Remove request
                FirebaseDatabase.getInstance()
                        .getReference("GPSRequests")
                        .child(r.requestId)
                        .removeValue();
            }
        }).addOnFailureListener(e -> {
            Log.e("FriendsFragment", "❌ Location error: " + e.getMessage());

            Toast.makeText(requireContext(),
                    "Could not get location. Please check GPS settings.",
                    Toast.LENGTH_LONG).show();

            // Remove request
            FirebaseDatabase.getInstance()
                    .getReference("GPSRequests")
                    .child(r.requestId)
                    .removeValue();
        });
    }

    // Update sendGpsRequest
    private void sendGpsRequest(Friend friend) {
        if (MainActivity.currentUser == null) return;

        String myUid = MainActivity.currentUser.getUid();
        String combinedKey = getCombinedRequestKey(myUid, friend.uid);

        FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(myUid)
                .child("username")
                .get()
                .addOnSuccessListener(snap -> {
                    String myUsername = snap.getValue(String.class);
                    if (myUsername == null) myUsername = "Unknown";

                    DatabaseReference sharesRef = FirebaseDatabase.getInstance()
                            .getReference("GPSShares")
                            .child(getCombinedRequestKey(myUid, friend.uid));

                    String finalMyUsername = myUsername;
                    sharesRef.get().addOnSuccessListener(snap2 -> {
                        if (snap2.exists()) {
                            GPSShare share = snap2.getValue(GPSShare.class);
                            if (share != null && !share.isExpired()) {
                                Toast.makeText(requireContext(),
                                        "Location already shared",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }

                        DatabaseReference ref = FirebaseDatabase.getInstance()
                                .getReference("GPSRequests")
                                .child(combinedKey);

                        Map<String, Object> data = new HashMap<>();
                        data.put("fromUid", myUid);
                        data.put("toUid", friend.uid);
                        data.put("fromUsername", finalMyUsername);
                        data.put("type", "GPS");
                        data.put("status", "PENDING");
                        data.put("timestamp", System.currentTimeMillis());

                        ref.setValue(data);
                        Log.d("FriendsFragment", "📍 GPS request sent to: " + friend.username);

                        // Immediately update UI
                        pendingGpsRequestUids.add(friend.uid);
                        friendsAdapter.setPendingGpsRequests(new HashSet<>(pendingGpsRequestUids));
                    });
                });
    }


    private void logoutBTNClick(View view) {
        FirebaseAuth.getInstance().signOut();
        MainActivity.currentUser = null;
        updateUI();
    }

    public void loginORSignBTNClick(View view) {
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        launcher.launch(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (requestsRef != null && requestsListener != null) {
            requestsRef.removeEventListener(requestsListener);
        }

        if (friendsRef != null && friendsListener != null) {
            friendsRef.removeEventListener(friendsListener);
        }

        if (usersRef != null && usersValueListener != null) {
            usersRef.removeEventListener(usersValueListener);
        }

        if (sentRequestsListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference("FriendRequests")
                    .removeEventListener(sentRequestsListener);
        }

        if (gpsRequestsListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference("GPSRequests")
                    .removeEventListener(gpsRequestsListener);
        }

        if (gpsSharesListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference("GPSShares")
                    .removeEventListener(gpsSharesListener);
        }
    }
}