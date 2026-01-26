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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {
        auth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences("login", MODE_PRIVATE);

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

    public void switchToMapTab() {
        runOnUiThread(() -> {
            if (selectedFragment != mapFragment) {
                hideFragment(selectedFragment);
                selectedFragment = mapFragment;
                showFragment(selectedFragment);
                bottomNavigationView.setSelectedItemId(R.id.mapMenu);
            }
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

    private void createFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.fragmentContainerView, fragment)
                .hide(fragment)
                .commit();
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .show(fragment)
                .commit();
    }

    private void hideFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .hide(fragment)
                .commit();
    }
}