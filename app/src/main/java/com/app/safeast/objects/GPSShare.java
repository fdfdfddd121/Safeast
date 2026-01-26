package com.app.safeast.objects;

public class GPSShare {
    public String fromUid;
    public String toUid;
    public String fromUsername;  // ADD THIS for display
    public double latitude;
    public double longitude;
    public long expiresAt;
    public long sharedAt;

    public GPSShare() {}

    public GPSShare(String fromUid, String toUid, double lat, double lng) {
        this.fromUid = fromUid;
        this.toUid = toUid;
        this.latitude = lat;
        this.longitude = lng;
        this.sharedAt = System.currentTimeMillis();
        this.expiresAt = this.sharedAt + 60000; // 60 seconds = 1 minute
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public int getRemainingSeconds() {
        long remaining = expiresAt - System.currentTimeMillis();
        return Math.max(0, (int)(remaining / 1000));
    }
}