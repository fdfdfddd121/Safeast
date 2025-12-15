package com.app.safeast.objects;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class UserMarker {
    private LatLng location;
    private int reactionTimeSec;
    private Marker marker;

    public UserMarker(LatLng location, GoogleMap googleMap) {
        this.location = location;
        this.marker = googleMap.addMarker(new MarkerOptions().position(location).title("USER"));
    }

    public UserMarker(LatLng location, int reactionTimeSec, GoogleMap googleMap) {
        this.location = location;
        this.reactionTimeSec = reactionTimeSec;
        this.marker = googleMap.addMarker(new MarkerOptions().position(location).title("USER"));
    }

    public LatLng getLocation() {
        return location;
    }

    public int getReactionTimeSec() {
        return reactionTimeSec;
    }

    public Marker getMarker() {
        return marker;
    }

    public void setReactionTimeSec(int reactionTimeSec) {
        this.reactionTimeSec = reactionTimeSec;
    }

    public void setLocation(LatLng location, GoogleMap googleMap) {
        this.location = location;
        this.marker = googleMap.addMarker(new MarkerOptions().position(location).title("USER"));
    }
}
