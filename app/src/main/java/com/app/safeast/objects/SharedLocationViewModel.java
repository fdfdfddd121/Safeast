package com.app.safeast.objects;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.app.safeast.objects.GPSShare;

import java.util.HashMap;
import java.util.Map;

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

    public void removeFriendLocation(String uid) {
        Map<String, GPSShare> current = friendLocations.getValue();
        if (current != null) {
            current.remove(uid);
            friendLocations.postValue(current);
        }
    }

    public void clearAll() {
        friendLocations.postValue(new HashMap<>());
    }
}