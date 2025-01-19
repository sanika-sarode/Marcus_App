package com.example.marcus;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private MediaPlayer splashSound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logoImageView = findViewById(R.id.logoImageView);
        Animation fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        logoImageView.startAnimation(fadeInAnimation);

        splashSound = MediaPlayer.create(this, R.raw.splash_sound2);
        splashSound.start();

        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, WelcomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

            if (splashSound != null) {
                splashSound.release();
                splashSound = null;
            }
        }, 3000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (splashSound != null) {
            splashSound.release();
            splashSound = null;
        }
    }
}