package com.app.safeast.objects;

public class Request {
    public String requestId;
    public String fromUid;
    public String toUid;  // ADD THIS
    public String fromUsername;
    public String type; // FRIEND | GPS
    public String status;
    public long timestamp;

    public Request() {}
}