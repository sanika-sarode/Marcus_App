package com.example.marcus;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SignupActivity extends AppCompatActivity {

    private EditText inputUsername, inputPassword, inputConfirmPassword;
    private Button signupButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        inputUsername = findViewById(R.id.inputUsername);
        inputPassword = findViewById(R.id.inputPassword);
        inputConfirmPassword = findViewById(R.id.inputConfirmPassword);
        signupButton = findViewById(R.id.signupButton);

        signupButton.setOnClickListener(v -> {
            if (validateInputs()) {
                saveCredentials(inputUsername.getText().toString(), inputPassword.getText().toString());
                Toast.makeText(SignupActivity.this, "Sign up successful!", Toast.LENGTH_SHORT).show();
                // Navigate to LoginActivity
                Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                startActivity(intent);
            }
        });
    }

    private boolean validateInputs() {
        if (TextUtils.isEmpty(inputUsername.getText().toString())) {
            inputUsername.setError("Username is required");
            return false;
        }

        if (TextUtils.isEmpty(inputPassword.getText().toString())) {
            inputPassword.setError("Password is required");
            return false;
        }

        if (TextUtils.isEmpty(inputConfirmPassword.getText().toString())) {
            inputConfirmPassword.setError("Confirm Password is required");
            return false;
        }

        if (!inputPassword.getText().toString().equals(inputConfirmPassword.getText().toString())) {
            inputConfirmPassword.setError("Passwords do not match");
            return false;
        }

        return true;
    }

    private void saveCredentials(String username, String password) {
        SharedPreferences sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("username", username);
        editor.putString("password", password);
        editor.apply();
    }
}