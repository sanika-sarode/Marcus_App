package com.example.marcus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class OptionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options);

        Button accountButton = findViewById(R.id.accountButton);
        Button logoutButton = findViewById(R.id.logoutButton);
        Button instructionButton = findViewById(R.id.instructionButton);
        Button closeButton = findViewById(R.id.closeButton);

        accountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start AccountActivity when the "Accounts" button is clicked
                Intent intent = new Intent(OptionsActivity.this, AccountActivity.class);
                startActivity(intent);
            }
        });

        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle logout button click
                Toast.makeText(OptionsActivity.this, "Logout clicked", Toast.LENGTH_SHORT).show();
                // Perform logout operation
            }
        });

        instructionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start InstructionsActivity when the "Instruction set" button is clicked
                Intent intent = new Intent(OptionsActivity.this, InstructionsActivity.class);
                startActivity(intent);
            }
        });

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle close button click
                finish(); // Close the OptionsActivity
            }
        });
    }
}