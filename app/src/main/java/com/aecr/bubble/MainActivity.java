package com.aecr.bubble;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView tvHighScore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvHighScore = findViewById(R.id.tv_high_score);
        updateHighScore();

        findViewById(R.id.btn_play).setOnClickListener(v -> {
            startActivity(new Intent(this, GameActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHighScore();
    }

    private void updateHighScore() {
        SharedPreferences prefs = getSharedPreferences("aecr_bubble", MODE_PRIVATE);
        int hs = prefs.getInt("high_score", 0);
        tvHighScore.setText(getString(R.string.high_score, hs));
    }
}
