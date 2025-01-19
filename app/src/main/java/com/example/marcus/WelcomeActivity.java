package com.example.marcus;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity implements WebSocketClientManager.WebSocketConnectionListener {

    private WebSocketClientManager webSocketClientManager;
    private Button signUpButton;
    private Button loginButton;
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        signUpButton = findViewById(R.id.signUpButton);
        loginButton = findViewById(R.id.loginButton);

        // Initially disable the buttons
        signUpButton.setEnabled(false);
        loginButton.setEnabled(false);

        // Set up onClickListeners for the buttons
        signUpButton.setOnClickListener(v -> {
            if (!isConnected) {
                Toast.makeText(WelcomeActivity.this, "First connect to server", Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(WelcomeActivity.this, UserDetailsActivity.class);
                startActivity(intent);
            }
        });

        loginButton.setOnClickListener(v -> {
            if (!isConnected) {
                Toast.makeText(WelcomeActivity.this, "First connect to server", Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
                startActivity(intent);
            }
        });

        webSocketClientManager = WebSocketClientManager.getInstance();
        webSocketClientManager.setWebSocketConnectionListener(this);
        webSocketClientManager.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        webSocketClientManager.disconnect();
    }

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            isConnected = true;
            // Enable the buttons when connected
            signUpButton.setEnabled(true);
            loginButton.setEnabled(true);
            Toast.makeText(WelcomeActivity.this, "Connected to server", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onCommandReceived(String command) {
        // Handle commands if necessary
        // If not needed, leave empty
    }
}