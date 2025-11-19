package com.app.safeast.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.safeast.R;
import com.google.firebase.Firebase;

public class LoginActivity extends AppCompatActivity {

    Button loginButton;
    EditText usernameEditText, passwordEditText, emailEditText;
    TextView email, modeSwitch;
    CheckBox rememberMe;
    boolean remember = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        init();
    }

    void init()
    {
        loginButton = findViewById(R.id.loginORSignBTN);
        usernameEditText = findViewById(R.id.username);
        passwordEditText = findViewById(R.id.password);
        emailEditText = findViewById(R.id.email);
        email = findViewById(R.id.emailTitle);
        modeSwitch = findViewById(R.id.switchMode);
        rememberMe = findViewById(R.id.saveLogin);
        email.setVisibility(View.GONE);
        emailEditText.setVisibility(View.GONE);
    }


    public void LoginUser(View view) {
    }

    public void registerUser(View view) {
    }

    public void switchMode(View view) {
        if(email.getVisibility() == View.GONE)
        {
            email.setVisibility(View.VISIBLE);
            emailEditText.setVisibility(View.VISIBLE);
            modeSwitch.setText("Have an account?");
            loginButton.setText("Register");
            loginButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    registerUser(view);
                    }
            });
        }
        else
        {
            email.setVisibility(View.GONE);
            emailEditText.setVisibility(View.GONE);
            modeSwitch.setText("Don't have an account?");
            loginButton.setText("Login");
            loginButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    LoginUser(view);
                }
            });
        }
    }

    public void saveLogin(View view) {
        if(rememberMe.isChecked())
        {
            remember = true;
        }
        else
        {
            remember = false;
        }
    }
}