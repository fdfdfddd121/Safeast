package com.app.safeast.activities;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.app.safeast.R;
import com.app.safeast.fragments.ChatFragment;
import com.app.safeast.fragments.FriendsFragment;
import com.app.safeast.fragments.MapFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    FragmentManager FM;
    FragmentContainerView fragmentContainerView;
    BottomNavigationView bottomNavigationView;
    Fragment mapFragment;
    ChatFragment chatFragment;
    FriendsFragment friendsFragment;
    Fragment selectedFragment;

    SharedPreferences sharedPreferences;
    FirebaseAuth auth;
    public static FirebaseUser currentUser = null;
    private FirebaseAuth.AuthStateListener authStateListener;

    //on create
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    //initialize all the variables
    private void init()
    {
        auth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences("login", MODE_PRIVATE);

        // The Firebase SDK automatically persists the user's session.
        // All we need to do is check who the current user is on startup.

        authStateListener = firebaseAuth -> {
            currentUser = firebaseAuth.getCurrentUser();
            if (chatFragment != null) {
                chatFragment.updateUI();
            }
            if (friendsFragment != null) {
                friendsFragment.updateUI();
            }
        };


        FM = getSupportFragmentManager();
        fragmentContainerView = findViewById(R.id.fragmentContainerView);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        mapFragment = new MapFragment();
        friendsFragment = new FriendsFragment();
        chatFragment = new ChatFragment();
        selectedFragment = mapFragment;
        createFragment(mapFragment);
        createFragment(friendsFragment);
        createFragment(chatFragment);
        showFragment(selectedFragment);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            hideFragment(selectedFragment);
            if (item.getItemId() == R.id.mapMenu) {
                selectedFragment = mapFragment;
            } else if (item.getItemId() == R.id.friendsMenu) {
                selectedFragment = friendsFragment;
            } else if (item.getItemId() == R.id.aiChatMenu) {
                selectedFragment = chatFragment;
            }
            showFragment(selectedFragment);
            return true;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        auth.addAuthStateListener(authStateListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (authStateListener != null) {
            auth.removeAuthStateListener(authStateListener);
        }
        if (auth.getCurrentUser() != null && !sharedPreferences.getBoolean("remember", false)) {
            auth.signOut();
        }
    }

    //creates a fragment view
    private void createFragment(Fragment fragment){
        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.fragmentContainerView, fragment)
                .hide(fragment)
                .commit();
    }

    //shows fragment view
    private void showFragment(Fragment fragment){
        getSupportFragmentManager()
                .beginTransaction()
                .show(fragment)
                .commit();
    }

    //hides fragment view
    private void hideFragment(Fragment fragment){
        getSupportFragmentManager()
                .beginTransaction()
                .hide(fragment)
                .commit();
    }

}
