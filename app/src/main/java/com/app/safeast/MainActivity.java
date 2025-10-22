package com.app.safeast;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

public class MainActivity extends AppCompatActivity {

    FragmentManager FM;
    FragmentContainerView fragmentContainerView;
    String fragmentName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init()
    {
        FM = getSupportFragmentManager();
        fragmentContainerView = findViewById(R.id.fragmentContainerView);
        fragmentName = "API";
    }

    public void switch_onClick(View view) {
        if (fragmentName.equals("API"))
        {
            FM.beginTransaction().setReorderingAllowed(true).replace(R.id.fragmentContainerView, MapFragment.class, null).commit();

            fragmentName = "MAP";
        }
        else {
            FM.beginTransaction().setReorderingAllowed(true).replace(R.id.fragmentContainerView, ApiFragment.class, null).commit();
            fragmentName = "API";
        }
    }

}