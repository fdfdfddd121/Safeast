package com.app.safeast.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.safeast.R;
import com.google.firebase.Firebase;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.auth.FirebaseAuth;

import java.util.HashMap;
import java.util.Map;

//the activity for logging in and registering to an account
public class LoginActivity extends AppCompatActivity {

    Button loginButton;
    EditText usernameEditText, passwordEditText, emailEditText;
    TextView username, modeSwitch;
    CheckBox rememberMe;
    boolean remember = false;
    SharedPreferences sp;

    FirebaseAuth auth;
    DatabaseReference reference;

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
    //initialize all the variables
    void init()
    {
        loginButton = findViewById(R.id.loginORSignBTN);
        usernameEditText = findViewById(R.id.username);
        passwordEditText = findViewById(R.id.password);
        emailEditText = findViewById(R.id.email);
        username = findViewById(R.id.userTitle);
        modeSwitch = findViewById(R.id.switchMode);
        rememberMe = findViewById(R.id.saveLogin);
        sp = getSharedPreferences("login", MODE_PRIVATE);
        username.setVisibility(View.GONE);
        usernameEditText.setVisibility(View.GONE);
        auth = FirebaseAuth.getInstance();
        reference = FirebaseDatabase.getInstance().getReference();
    }

    //button function to login the user
    public void LoginUser(View view) {
        String email = emailEditText.getText().toString();
        String password = passwordEditText.getText().toString();
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Login success
                if(remember)
                {
                    SharedPreferences.Editor editor = sp.edit();
                    editor.putString("email", email);
                    editor.putString("password", password);
                    editor.apply();
                }
                else{
                    SharedPreferences.Editor editor = sp.edit();
                    editor.remove("email");
                    editor.remove("password");
                    editor.apply();
                }
                //startActivity(new Intent(MainActivity.this, DataActivity.class));
            } else {
                // Login failed, check if user does not exist
                String message = task.getException().getMessage();
                if (message != null && message.contains("There is no user record")) {
                    Toast.makeText(this, "User not found! Please register first.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Login failed: " + message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    //button function to register the user
    public void registerUser(View view) {
        String email = emailEditText.getText().toString();
        String password = passwordEditText.getText().toString();
        String username = usernameEditText.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Fill email + password", Toast.LENGTH_SHORT).show();
            return;
        }
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String uid = auth.getCurrentUser().getUid();
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("email", email);
                userMap.put("username", username);
                DatabaseReference dbRef = reference.child("Users");
                dbRef.child(uid).setValue(userMap).addOnCompleteListener(task2 -> {
                    if (task2.isSuccessful()) {
                        if(remember)
                        {
                            SharedPreferences.Editor editor = sp.edit();
                            editor.putString("email", email);
                            editor.putString("password", password);
                            editor.apply();
                        }
                        Toast.makeText(this, "Registered!", Toast.LENGTH_SHORT).show();
                        //Intent intent = new Intent(MainActivity.this, DataActivity.class);
                        //startActivity(intent);
                    }
                });
            } else {
                Toast.makeText(this, "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    //switch between register and login
    public void switchMode(View view) {
        if(username.getVisibility() == View.GONE)
        {
            username.setVisibility(View.VISIBLE);
            usernameEditText.setVisibility(View.VISIBLE);
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
            username.setVisibility(View.GONE);
            usernameEditText.setVisibility(View.GONE);
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

    //switch if the user wants to save the login
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