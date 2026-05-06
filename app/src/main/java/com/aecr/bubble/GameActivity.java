package com.aecr.bubble;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

public class GameActivity extends AppCompatActivity implements GameView.GameEventListener {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        gameView = new GameView(this);
        gameView.setListener(this);
        setContentView(gameView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // GameLoop tự dừng khi surface destroyed
    }

    @Override
    public void onLevelClear(int score, int level) {
        saveHighScore(score);
        new AlertDialog.Builder(this)
                .setTitle("🎉 Qua màn " + level + "!")
                .setMessage("Điểm: " + score + "\nBạn muốn tiếp tục?")
                .setPositiveButton("Màn tiếp", (d, w) -> {
                    gameView.getEngine().nextLevel();
                    // Khởi động lại game loop
                    restartGameView();
                })
                .setNegativeButton("Thoát", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    @Override
    public void onGameOver(int score) {
        saveHighScore(score);
        int hs = getSharedPreferences("aecr_bubble", MODE_PRIVATE).getInt("high_score", 0);
        new AlertDialog.Builder(this)
                .setTitle("💥 Thua rồi!")
                .setMessage("Điểm: " + score + "\nĐiểm cao nhất: " + hs)
                .setPositiveButton("Chơi lại", (d, w) -> {
                    gameView.getEngine().restart();
                    restartGameView();
                })
                .setNegativeButton("Thoát", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void restartGameView() {
        // Gắn lại surface để khởi động loop mới
        setContentView(gameView);
    }

    private void saveHighScore(int score) {
        SharedPreferences prefs = getSharedPreferences("aecr_bubble", MODE_PRIVATE);
        int current = prefs.getInt("high_score", 0);
        if (score > current)
            prefs.edit().putInt("high_score", score).apply();
    }
}
