package com.app.safeast.objects;

public class User {
    public String uid;
    public String username;

    public User() {} // Firebase

    public User(String uid, String username) {
        this.uid = uid;
        this.username = username;
    }

    public String getUid() {
        return uid;
    }
}
