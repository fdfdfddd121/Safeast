package com.app.safeast.managers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.app.safeast.helperFiles.GPSEnabler;
import com.app.safeast.objects.Friend;
import com.app.safeast.objects.GPSShare;
import com.app.safeast.objects.Request;
import com.app.safeast.objects.User;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationTokenSource;
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

public class FriendsManager {

    /* ===================== CALLBACK ===================== */

    public interface Callbacks {
        void onUsersUpdated(List<User> users);
        void onFriendsUpdated(List<Friend> friends);
        void onRequestsUpdated(List<Request> requests);
        void onPendingFriendRequestsUpdated(Set<String> pendingFriendUids);
        void onPendingGpsUpdated(Set<String> pendingGpsUids);
        void onGpsShareReceived(GPSShare share, boolean isNew);
        void onGpsShareExpired(String fromUid);
        void onGpsApprovalSuccess(String toUsername);
        void onGpsApprovalFailed(String reason);
    }

    private final Callbacks callbacks;
    private final String myUid;
    private final Context context;
    private final FusedLocationProviderClient locationClient;

    /* ===================== STATE ===================== */

    private final List<User> users = new ArrayList<>();
    private final List<Friend> friends = new ArrayList<>();

    // Separate lists for each type
    private final List<Request> friendRequests = new ArrayList<>();
    private final List<Request> gpsRequests = new ArrayList<>();

    private final Set<String> friendUids = new HashSet<>();
    private final Set<String> pendingFriendUids = new HashSet<>();
    private final Set<String> pendingGpsUids = new HashSet<>();
    private final Set<String> knownGpsShares = new HashSet<>();

    /* ===================== FIREBASE ===================== */

    private final DatabaseReference db = FirebaseDatabase.getInstance().getReference();

    private ValueEventListener usersListener;
    private ValueEventListener friendsListener;
    private ValueEventListener friendRequestsListener;
    private ValueEventListener gpsRequestsListener;
    private ValueEventListener gpsSharesListener;

    private final Handler expirationHandler = new Handler(Looper.getMainLooper());
    private Runnable expirationCheckRunnable;

    /* ===================== CONSTRUCTOR ===================== */

    public FriendsManager(@NonNull String myUid, @NonNull Context context, @NonNull Callbacks callbacks) {
        this.myUid = myUid;
        this.context = context;
        this.callbacks = callbacks;
        this.locationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    /* ===================== LIFECYCLE ===================== */

    public void start() {
        listenFriends();
        listenFriendRequests();
        listenGpsRequests();
        listenGpsShares();
        listenUsers();
    }

    public void stop() {
        if (usersListener != null) db.child("Users").removeEventListener(usersListener);
        if (friendsListener != null) db.child("Friends").child(myUid).removeEventListener(friendsListener);
        if (friendRequestsListener != null) db.child("FriendRequests").removeEventListener(friendRequestsListener);
        if (gpsRequestsListener != null) db.child("GPSRequests").removeEventListener(gpsRequestsListener);
        if (gpsSharesListener != null) db.child("GPSShares").removeEventListener(gpsSharesListener);

        // Cancel expiration checks
        if (expirationCheckRunnable != null) {
            expirationHandler.removeCallbacks(expirationCheckRunnable);
        }
    }

    /* ===================== USERS ===================== */

    private void listenUsers() {
        usersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                users.clear();

                for (DataSnapshot snap : snapshot.getChildren()) {
                    String uid = snap.getKey();
                    if (uid == null || uid.equals(myUid)) continue;
                    if (friendUids.contains(uid)) continue;

                    String username = snap.child("username").getValue(String.class);
                    if (username != null) {
                        users.add(new User(uid, username));
                    }
                }

                callbacks.onUsersUpdated(new ArrayList<>(users));
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        db.child("Users").addValueEventListener(usersListener);
    }

    /* ===================== FRIENDS ===================== */

    private void listenFriends() {
        friendsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Set<String> oldFriendUids = new HashSet<>(friendUids);
                friendUids.clear();

                // Collect all current friend UIDs from Firebase
                Set<String> currentFriendUids = new HashSet<>();

                for (DataSnapshot snap : snapshot.getChildren()) {
                    String uid = snap.getKey();
                    if (uid != null) {
                        currentFriendUids.add(uid);
                    }
                }

                // Remove friends that are no longer in Firebase
                friends.removeIf(friend -> !currentFriendUids.contains(friend.uid));

                // Add new friends
                for (String uid : currentFriendUids) {
                    friendUids.add(uid);

                    // Only fetch if we don't already have this friend
                    boolean alreadyHave = false;
                    for (Friend f : friends) {
                        if (f.uid.equals(uid)) {
                            alreadyHave = true;
                            break;
                        }
                    }

                    if (!alreadyHave) {
                        fetchFriend(uid);
                    } else {
                        // Already have this friend, just notify UI
                        callbacks.onFriendsUpdated(new ArrayList<>(friends));
                    }
                }

                // If friends changed, refresh users list
                if (!friendUids.equals(oldFriendUids)) {
                    refreshUsers();
                }

                // Notify even if no new friends (for removals)
                if (currentFriendUids.isEmpty() || friends.size() == currentFriendUids.size()) {
                    callbacks.onFriendsUpdated(new ArrayList<>(friends));
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        db.child("Friends").child(myUid).addValueEventListener(friendsListener);
    }

    private void fetchFriend(String uid) {
        db.child("Users").child(uid).child("username").get()
                .addOnSuccessListener(snap -> {
                    String username = snap.getValue(String.class);
                    if (username != null) {
                        friends.add(new Friend(uid, username));
                        callbacks.onFriendsUpdated(new ArrayList<>(friends));
                    }
                });
    }

    private void refreshUsers() {
        db.child("Users").get().addOnSuccessListener(snapshot -> {
            users.clear();
            for (DataSnapshot snap : snapshot.getChildren()) {
                String uid = snap.getKey();
                if (uid == null || uid.equals(myUid)) continue;
                if (friendUids.contains(uid)) continue;

                String username = snap.child("username").getValue(String.class);
                if (username != null) {
                    users.add(new User(uid, username));
                }
            }
            callbacks.onUsersUpdated(new ArrayList<>(users));
        });
    }

    public void removeFriend(String friendUid) {
        db.child("Friends").child(myUid).child(friendUid).removeValue();
        db.child("Friends").child(friendUid).child(myUid).removeValue();
    }

    /* ===================== FRIEND REQUESTS ===================== */

    private void listenFriendRequests() {
        friendRequestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                friendRequests.clear();
                pendingFriendUids.clear();

                Log.d("FriendsManager", "========== FRIEND REQUESTS UPDATED ==========");
                Log.d("FriendsManager", "Total friend requests in DB: " + snapshot.getChildrenCount());

                for (DataSnapshot snap : snapshot.getChildren()) {
                    Request r = snap.getValue(Request.class);
                    if (r == null || !"FRIEND".equals(r.type)) continue;

                    r.requestId = snap.getKey();

                    if (myUid.equals(r.toUid)) {
                        // I RECEIVED this request
                        friendRequests.add(r);
                        pendingFriendUids.add(r.fromUid);
                        Log.d("FriendsManager", "📬 Received request from: " + r.fromUid);
                    } else if (myUid.equals(r.fromUid)) {
                        // I SENT this request
                        pendingFriendUids.add(r.toUid);
                        Log.d("FriendsManager", "✉️ Sent request to: " + r.toUid);
                    }
                }

                Log.d("FriendsManager", "Total pending friend UIDs: " + pendingFriendUids.size());
                Log.d("FriendsManager", "Pending UIDs: " + pendingFriendUids);

                updateCombinedRequests();
                callbacks.onPendingFriendRequestsUpdated(new HashSet<>(pendingFriendUids));
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        db.child("FriendRequests").addValueEventListener(friendRequestsListener);
    }

    public void approveFriend(Request r) {
        db.child("Friends").child(myUid).child(r.fromUid).setValue(true);
        db.child("Friends").child(r.fromUid).child(myUid).setValue(true);
        db.child("FriendRequests").child(r.requestId).removeValue();
    }

    public void declineRequest(Request r) {
        if ("FRIEND".equals(r.type)) {
            db.child("FriendRequests").child(r.requestId).removeValue();
        } else if ("GPS".equals(r.type)) {
            db.child("GPSRequests").child(r.requestId).removeValue();
        }
    }

    /* ===================== GPS REQUESTS ===================== */

    private void listenGpsRequests() {
        gpsRequestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("FriendsManager", "========== GPS REQUESTS UPDATED ==========");

                gpsRequests.clear();
                pendingGpsUids.clear();

                for (DataSnapshot snap : snapshot.getChildren()) {
                    Request r = snap.getValue(Request.class);
                    if (r == null || !"GPS".equals(r.type)) continue;

                    r.requestId = snap.getKey();

                    Log.d("FriendsManager", "GPS Request: from=" + r.fromUid + ", to=" + r.toUid + ", myUid=" + myUid);

                    // INCOMING: Someone is asking ME for GPS - show in MY requests list
                    if (r.toUid.equals(myUid)) {
                        gpsRequests.add(r);
                        Log.d("FriendsManager", "✅ INCOMING request from " + r.fromUsername);
                    }

                    // OUTGOING: I asked THEM for GPS
                    // Their button on MY screen should show pending
                    else if (r.fromUid.equals(myUid)) {
                        pendingGpsUids.add(r.toUid);  // Add THEM to pending
                        Log.d("FriendsManager", "⏳ OUTGOING request to " + r.toUid + " - marking THEM as pending");
                    }
                }

                Log.d("FriendsManager", "GPS Requests count: " + gpsRequests.size());
                Log.d("FriendsManager", "Pending GPS UIDs (friends whose buttons should show pending): " + pendingGpsUids);

                updateCombinedRequests();
                updatePendingGps();
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        db.child("GPSRequests").addValueEventListener(gpsRequestsListener);
    }

    /* ===================== GPS SHARES ===================== */

    // Tracks friends who are currently sharing their location WITH me
    private final Set<String> receivingSharesFromUids = new HashSet<>();

    private void listenGpsShares() {
        gpsSharesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Set<String> current = new HashSet<>();
                Set<String> previousReceiving = new HashSet<>(receivingSharesFromUids);
                receivingSharesFromUids.clear();

                long nextExpiration = Long.MAX_VALUE;

                for (DataSnapshot snap : snapshot.getChildren()) {
                    GPSShare share = snap.getValue(GPSShare.class);
                    if (share == null) continue;

                    if (share.isExpired()) {
                        Log.d("FriendsManager", "⏰ Share expired: " + snap.getKey() + " from=" + share.fromUid + " to=" + share.toUid);
                        snap.getRef().removeValue();

                        // Notify receiver that share expired
                        if (myUid.equals(share.toUid)) {
                            callbacks.onGpsShareExpired(share.fromUid);
                        }
                        continue;
                    }

                    // Track when this share will expire
                    if (share.expiresAt < nextExpiration) {
                        nextExpiration = share.expiresAt;
                    }

                    // RECEIVING: Someone is sharing their location WITH me
                    // Their button on MY screen should show pending during the share
                    if (myUid.equals(share.toUid)) {
                        current.add(share.fromUid);
                        receivingSharesFromUids.add(share.fromUid);
                        boolean isNew = !knownGpsShares.contains(share.fromUid);
                        callbacks.onGpsShareReceived(share, isNew);

                        Log.d("FriendsManager", "📍 RECEIVING share from " + share.fromUid + " - marking THEM as pending");
                    }
                }

                // Detect shares that just expired (were in previous set, not in current)
                Set<String> expired = new HashSet<>(previousReceiving);
                expired.removeAll(receivingSharesFromUids);

                for (String expiredUid : expired) {
                    Log.d("FriendsManager", "⏰ Share from " + expiredUid + " no longer active - clearing pending");
                    callbacks.onGpsShareExpired(expiredUid);
                }

                knownGpsShares.clear();
                knownGpsShares.addAll(current);

                updatePendingGps();

                // Schedule next check at the soonest expiration time
                scheduleExpirationCheck(nextExpiration);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        db.child("GPSShares").addValueEventListener(gpsSharesListener);
    }

    private void scheduleExpirationCheck(long expiresAt) {
        // Cancel previous check
        if (expirationCheckRunnable != null) {
            expirationHandler.removeCallbacks(expirationCheckRunnable);
        }

        if (expiresAt == Long.MAX_VALUE) {
            return; // No active shares
        }

        long delay = expiresAt - System.currentTimeMillis() + 100; // Small buffer
        if (delay < 0) delay = 0;

        Log.d("FriendsManager", "⏰ Scheduling expiration check in " + (delay/1000.0) + "s for timestamp " + expiresAt);

        expirationCheckRunnable = () -> {
            Log.d("FriendsManager", "⏰ Expiration check FIRED at " + System.currentTimeMillis());

            // Manually check and remove expired shares
            db.child("GPSShares").get().addOnSuccessListener(snapshot -> {
                long now = System.currentTimeMillis();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    GPSShare share = snap.getValue(GPSShare.class);
                    if (share != null && share.expiresAt <= now) {
                        Log.d("FriendsManager", "⏰ Removing expired share: " + snap.getKey());
                        snap.getRef().removeValue();
                    }
                }
            });
        };

        expirationHandler.postDelayed(expirationCheckRunnable, delay);
    }

    /**
     * CRITICAL LOGIC:
     * pendingGpsUids contains UIDs of friends whose GPS button should show "PENDING" on MY screen
     *
     * This includes:
     * 1. Friends I sent a GPS request TO (waiting for them to approve)
     * 2. Friends who are actively sharing their location WITH me (1 min timer active)
     */
    private void updatePendingGps() {
        Set<String> combined = new HashSet<>();

        // Case 1: I sent request to them - their button shows pending while I wait
        combined.addAll(pendingGpsUids);

        // Case 2: They approved and are sharing - their button stays pending during the 1min share
        combined.addAll(receivingSharesFromUids);

        Log.d("FriendsManager", "📍 Pending from requests I sent: " + pendingGpsUids);
        Log.d("FriendsManager", "📍 Pending from shares I'm receiving: " + receivingSharesFromUids);
        Log.d("FriendsManager", "📍 Total pending GPS (friends whose buttons show pending): " + combined.size());

        callbacks.onPendingGpsUpdated(combined);
    }

    // COMBINE BOTH REQUEST LISTS
    private void updateCombinedRequests() {
        List<Request> combined = new ArrayList<>();
        combined.addAll(friendRequests);
        combined.addAll(gpsRequests);
        callbacks.onRequestsUpdated(combined);
    }

    public void sendGpsRequest(String toUid, String fromUsername) {
        String key = combinedKey(myUid, toUid);

        Map<String, Object> data = new HashMap<>();
        data.put("fromUid", myUid);
        data.put("toUid", toUid);
        data.put("fromUsername", fromUsername);
        data.put("type", "GPS");
        data.put("timestamp", System.currentTimeMillis());

        db.child("GPSRequests").child(key).setValue(data);
    }

    public void approveGpsRequest(Request r) {
        Log.d("FriendsManager", "========== APPROVING GPS REQUEST ==========");

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            callbacks.onGpsApprovalFailed("Location permission required");
            db.child("GPSRequests").child(r.requestId).removeValue();
            return;
        }

        // INLINE GPS CHECK - Show dialog if GPS is off
        GPSEnabler.checkAndEnableGPS(context,
                () -> {
                    // GPS is enabled - get location and share
                    locationClient.getCurrentLocation(
                            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                            new CancellationTokenSource().getToken()
                    ).addOnSuccessListener(location -> {
                        if (location != null) {
                            double lat = location.getLatitude();
                            double lng = location.getLongitude();

                            Log.d("FriendsManager", "📍 Got location: " + lat + ", " + lng);

                            db.child("Users").child(myUid).child("username").get()
                                    .addOnSuccessListener(snap -> {
                                        String myUsername = snap.getValue(String.class);
                                        if (myUsername == null) myUsername = "Friend";

                                        String key = combinedKey(myUid, r.fromUid);

                                        Map<String, Object> shareData = new HashMap<>();
                                        shareData.put("fromUid", myUid);
                                        shareData.put("toUid", r.fromUid);
                                        shareData.put("fromUsername", myUsername);
                                        shareData.put("latitude", lat);
                                        shareData.put("longitude", lng);
                                        shareData.put("sharedAt", System.currentTimeMillis());
                                        shareData.put("expiresAt", System.currentTimeMillis() + 60000);

                                        db.child("GPSShares").child(key).setValue(shareData)
                                                .addOnSuccessListener(aVoid -> {
                                                    Log.d("FriendsManager", "✅ GPS shared successfully");
                                                    db.child("GPSRequests").child(r.requestId).removeValue()
                                                            .addOnSuccessListener(aVoid2 -> {
                                                                callbacks.onGpsApprovalSuccess(r.fromUsername);
                                                            });
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e("FriendsManager", "❌ Failed to share GPS: " + e.getMessage());
                                                    callbacks.onGpsApprovalFailed("Failed to share location");
                                                    db.child("GPSRequests").child(r.requestId).removeValue();
                                                });
                                    });
                        } else {
                            Log.e("FriendsManager", "❌ Location is null");
                            callbacks.onGpsApprovalFailed("Could not get location. Please try again.");
                            db.child("GPSRequests").child(r.requestId).removeValue();
                        }
                    }).addOnFailureListener(e -> {
                        Log.e("FriendsManager", "❌ Location error: " + e.getMessage());
                        callbacks.onGpsApprovalFailed("Location error: " + e.getMessage());
                        db.child("GPSRequests").child(r.requestId).removeValue();
                    });
                },
                () -> {
                    // GPS disabled and user declined
                    callbacks.onGpsApprovalFailed("GPS is required to share location");
                    db.child("GPSRequests").child(r.requestId).removeValue();
                }
        );
    }

    /* ===================== UTIL ===================== */

    private String combinedKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }
}