package com.example.kas_app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        MainActivity.applySavedTheme(newBase);
        super.attachBaseContext(newBase);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);

        // Night mode is applied before attachBaseContext. Locale is applied here.
        MainActivity.applySavedLocale(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Populate database from CSV on launch
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        dbHelper.importStudentsFromCSV(this);

        MaterialButton btnBegin = findViewById(R.id.btnBegin);
        btnBegin.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }
}