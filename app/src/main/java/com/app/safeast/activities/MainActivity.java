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
        init(savedInstanceState);
    }

    private void init(Bundle savedInstanceState) {
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

        if (savedInstanceState == null) {
            mapFragment = new MapFragment();
            friendsFragment = new FriendsFragment();
            chatFragment = new ChatFragment();
            selectedFragment = mapFragment;
            createFragment(mapFragment, "map");
            createFragment(friendsFragment, "friends");
            createFragment(chatFragment, "chat");
            showFragment(selectedFragment);
        } else {
            mapFragment = FM.findFragmentByTag("map");
            friendsFragment = (FriendsFragment) FM.findFragmentByTag("friends");
            chatFragment = (ChatFragment) FM.findFragmentByTag("chat");

            int selectedId = bottomNavigationView.getSelectedItemId();
            if (selectedId == R.id.mapMenu) {
                selectedFragment = mapFragment;
            } else if (selectedId == R.id.friendsMenu) {
                selectedFragment = friendsFragment;
            } else if (selectedId == R.id.aiChatMenu) {
                selectedFragment = chatFragment;
            }
        }

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

    private void createFragment(Fragment fragment, String tag) {
        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.fragmentContainerView, fragment, tag)
                .hide(fragment)
                .commit();
    }

    private void showFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .show(fragment)
                    .commit();
        }
    }

    private void hideFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .hide(fragment)
                    .commit();
        }
    }
}