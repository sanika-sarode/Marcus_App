package com.example.marcus;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;

public class UserDetailsActivity extends AppCompatActivity {

    private EditText inputName, inputEmail, inputPhone, inputDob, inputAge;
    private Button nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_details);

        inputName = findViewById(R.id.inputName);
        inputEmail = findViewById(R.id.inputEmail);
        inputPhone = findViewById(R.id.inputPhone);
        inputDob = findViewById(R.id.inputDob);
        inputAge = findViewById(R.id.inputAge);
        nextButton = findViewById(R.id.nextButton);

        inputDob.setOnClickListener(v -> showDatePickerDialog());

        nextButton.setOnClickListener(v -> {
            if (validateInputs()) {
                // Proceed to SignupActivity
                Toast.makeText(UserDetailsActivity.this, "Details saved successfully!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(UserDetailsActivity.this, SignupActivity.class);
                startActivity(intent);
            }
        });
    }

    private void showDatePickerDialog() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                UserDetailsActivity.this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    String dob = selectedDay + "/" + (selectedMonth + 1) + "/" + selectedYear;
                    inputDob.setText(dob);
                    calculateAge(selectedYear, selectedMonth, selectedDay);
                },
                year, month, day);
        datePickerDialog.show();
    }

    private void calculateAge(int year, int month, int day) {
        final Calendar dob = Calendar.getInstance();
        final Calendar today = Calendar.getInstance();

        dob.set(year, month, day);

        int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);

        if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)){
            age--;
        }

        inputAge.setText(String.valueOf(age));
    }

    private boolean validateInputs() {
        if (TextUtils.isEmpty(inputName.getText().toString())) {
            inputName.setError("Name is required");
            return false;
        }

        if (TextUtils.isEmpty(inputEmail.getText().toString())) {
            inputEmail.setError("Email is required");
            return false;
        }

        if (TextUtils.isEmpty(inputPhone.getText().toString())) {
            inputPhone.setError("Phone number is required");
            return false;
        }

        if (inputPhone.getText().toString().length() != 10) {
            inputPhone.setError("Phone number must be 10 digits");
            return false;
        }

        if (TextUtils.isEmpty(inputDob.getText().toString())) {
            inputDob.setError("Date of Birth is required");
            return false;
        }

        if (TextUtils.isEmpty(inputAge.getText().toString())) {
            inputAge.setError("Age is required");
            return false;
        }

        return true;
    }
}