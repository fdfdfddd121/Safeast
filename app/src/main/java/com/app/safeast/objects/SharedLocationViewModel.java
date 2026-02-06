package com.app.safeast.objects;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.app.safeast.objects.GPSShare;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SharedLocationViewModel extends ViewModel {

    private final MutableLiveData<Map<String, GPSShare>> friendLocations = new MutableLiveData<>(new HashMap<>());

    public LiveData<Map<String, GPSShare>> getFriendLocations() {
        return friendLocations;
    }

    public void updateFriendLocation(String uid, GPSShare share) {
        Map<String, GPSShare> current = friendLocations.getValue();
        if (current == null) current = new HashMap<>();

        current.put(uid, share);
        friendLocations.postValue(current);  // postValue is thread-safe
    }
// CORRECTED VERSION of removeFriendLocation in your ViewModel

    public void removeFriendLocation(String fromUid) {
        Map<String, GPSShare> current = friendLocations.getValue();
        if (current != null && current.containsKey(fromUid)) {
            GPSShare share = current.get(fromUid);
            if (share != null) {
                // Remove from Firebase using the combined key
                String key = combinedKey(share.fromUid, share.toUid);
                FirebaseDatabase.getInstance()
                        .getReference("GPSShares")
                        .child(key)
                        .removeValue()
                        .addOnSuccessListener(aVoid -> {
                            Log.d("ViewModel", "✅ Removed GPS share from Firebase: " + key);
                        })
                        .addOnFailureListener(e -> {
                            Log.e("ViewModel", "❌ Failed to remove GPS share: " + e.getMessage());
                        });
            }

            // Remove from local state
            current.remove(fromUid);
            friendLocations.postValue(current);
        }
    }

    public void clearAll() {
        friendLocations.postValue(new HashMap<>());
    }

    private String combinedKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }
}