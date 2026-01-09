package com.app.safeast.fragments;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.app.safeast.R;
import com.app.safeast.activities.LoginActivity;
import com.app.safeast.activities.MainActivity;
import com.app.safeast.managers.ChatManager;
import com.app.safeast.objects.ChatAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ChatFragment extends Fragment {

    // --- UI Views ---
    Button sendBT;
    EditText messageET;
    RecyclerView recyclerView;
    Button loginORSignBTN;
    LinearLayout chatLayout;

    // --- Chat & Adapter ---
    private final List<String> messagesList = new ArrayList<>();
    private ChatAdapter chatAdapter;
    private ChatManager chatManager;

    ActivityResultLauncher<Intent> launcher;

    public ChatFragment() {
        // Required empty public constructor
    }

    public static ChatFragment newInstance() {
        return new ChatFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // --- Initialize Views ---
        sendBT = view.findViewById(R.id.sendBT);
        messageET = view.findViewById(R.id.messageET);
        recyclerView = view.findViewById(R.id.chatRV);
        loginORSignBTN = view.findViewById(R.id.loginORSignBTN);
        chatLayout = view.findViewById(R.id.chatLayout);

        // --- Setup RecyclerView & Adapter ---
        chatAdapter = new ChatAdapter(messagesList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(chatAdapter);

        // --- Initialize ChatManager ---
        chatManager = ChatManager.getInstance();

        // Because the initial message is fetched in the background, we need to wait for it.
        // This thread will wait for chatManager.response to have a value.
        new Thread(() -> {
            try {
                int waitTime = 0;
                while (chatManager.response == null && waitTime < 5000) { // Wait max 5 seconds
                    Thread.sleep(100);
                    waitTime += 100;
                }
                // Once the response is ready, update the UI on the main thread.
                if (getActivity() != null && chatManager.response != null) {
                    getActivity().runOnUiThread(() -> {
                        addMessageToList(chatManager.response, false);
                    });
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        // --- Handle Login State ---
        launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == MainActivity.RESULT_OK && result.getData() != null) {
                        MainActivity.currentUser = result.getData().getParcelableExtra("user");
                        updateLoginUI();
                    }
                });
        updateLoginUI();

        // --- Set Click Listeners ---
        loginORSignBTN.setOnClickListener(this::loginORSignBTNClick);
        sendBT.setOnClickListener(this::sendBTClick);
    }

    private void updateLoginUI() {
        if (MainActivity.currentUser != null) {
            loginORSignBTN.setVisibility(View.GONE);
            chatLayout.setVisibility(View.VISIBLE);
        } else {
            loginORSignBTN.setVisibility(View.VISIBLE);
            chatLayout.setVisibility(View.GONE);
        }
    }

    public void sendBTClick(View view) {
        String message = messageET.getText().toString().trim();
        if (!message.isEmpty()) {
            addMessageToList(message, true);
            messageET.setText("");

            // Use the correct ChatManager.ChatCallback interface
            chatManager.sendChatMessage(message, new ChatManager.ChatCallback() {
                @Override
                public void onSuccess(String result) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            addMessageToList("Bot: " + result, false);
                        });
                    }
                }

                @Override
                public void onFailure(Throwable error) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        }
    }

    private void addMessageToList(String message, boolean isUser) {
        if (isUser) {
            message = "You: " + message;
        }
        messagesList.add(message);
        chatAdapter.notifyItemInserted(messagesList.size() - 1);
        recyclerView.scrollToPosition(messagesList.size() - 1); // Auto-scroll
    }

    public void loginORSignBTNClick(View view) {
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        launcher.launch(intent);
    }
}
