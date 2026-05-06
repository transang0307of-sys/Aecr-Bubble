package com.aecr.bubble;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.List;

public class GameView extends SurfaceView implements SurfaceHolder.Callback {

    // ---- Game Loop ----
    private static final long FRAME_MS = 16L;   // ~60 FPS

    private GameEngine engine;
    private GameLoop   gameLoop;
    private Paint      paint;
    private Handler    mainHandler;

    public interface GameEventListener {
        void onLevelClear(int score, int level);
        void onGameOver(int score);
    }

    private GameEventListener listener;

    public GameView(Context context) { super(context); init(); }
    public GameView(Context context, AttributeSet a) { super(context, a); init(); }

    private void init() {
        getHolder().addCallback(this);
        setFocusable(true);
        paint       = new Paint(Paint.ANTI_ALIAS_FLAG);
        mainHandler = new Handler(Looper.getMainLooper());
        engine      = new GameEngine();
    }

    public void setListener(GameEventListener l) { listener = l; }
    public GameEngine getEngine() { return engine; }

    // ---- SurfaceHolder.Callback ----

    @Override public void surfaceCreated(SurfaceHolder h) {
        engine.init(getWidth(), getHeight());
        gameLoop = new GameLoop(h);
        gameLoop.running = true;
        gameLoop.start();
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
        engine.init(w, hh);
    }

    @Override public void surfaceDestroyed(SurfaceHolder h) {
        if (gameLoop != null) {
            gameLoop.running = false;
            try { gameLoop.join(300); } catch (InterruptedException ignored) {}
        }
    }

    // ---- Touch ----

    private float touchStartX, touchStartY;

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        float x = ev.getX(), y = ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = x; touchStartY = y;
                engine.setAim(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                engine.setAim(x, y);
                break;
            case MotionEvent.ACTION_UP:
                engine.shoot(x, y);
                break;
        }
        return true;
    }

    // ---- Game Loop Thread ----

    private class GameLoop extends Thread {
        private final SurfaceHolder holder;
        volatile boolean running;

        GameLoop(SurfaceHolder h) { holder = h; }

        @Override public void run() {
            while (running) {
                long start = System.currentTimeMillis();
                engine.update();

                // Kiểm tra state events
                GameEngine.State st = engine.state;
                if (st == GameEngine.State.LEVEL_CLEAR && listener != null) {
                    final int sc = engine.score, lv = engine.level;
                    mainHandler.post(() -> listener.onLevelClear(sc, lv));
                    running = false;
                    break;
                }
                if (st == GameEngine.State.GAME_OVER && listener != null) {
                    final int sc = engine.score;
                    mainHandler.post(() -> listener.onGameOver(sc));
                    running = false;
                    break;
                }

                Canvas canvas = null;
                try {
                    canvas = holder.lockCanvas();
                    if (canvas != null) synchronized (holder) { drawFrame(canvas); }
                } finally {
                    if (canvas != null) holder.unlockCanvasAndPost(canvas);
                }

                long elapsed = System.currentTimeMillis() - start;
                long sleep   = FRAME_MS - elapsed;
                if (sleep > 0) try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
            }
        }
    }

    // ---- Drawing ----

    private void drawFrame(Canvas c) {
        drawBackground(c);
        drawGrid(c);
        drawFalling(c);
        drawPopping(c);
        drawAimLine(c);
        drawBall(c);
        drawCannon(c);
        drawHUD(c);
    }

    private void drawBackground(Canvas c) {
        // Nền gradient tối
        paint.setShader(new LinearGradient(0, 0, 0, engine.screenH,
                Color.rgb(13, 13, 43), Color.rgb(5, 5, 20), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, engine.screenW, engine.screenH, paint);
        paint.setShader(null);

        // Vẽ ngôi sao nhỏ (fake stars dựa trên seed cố định)
        paint.setColor(0x55FFFFFF);
        paint.setStyle(Paint.Style.FILL);
        float[] stars = {0.1f,0.05f, 0.3f,0.15f, 0.6f,0.08f, 0.8f,0.03f,
                         0.2f,0.25f, 0.5f,0.22f, 0.75f,0.18f, 0.9f,0.12f,
                         0.4f,0.35f, 0.7f,0.30f, 0.15f,0.40f, 0.55f,0.42f};
        for (int i = 0; i < stars.length; i += 2) {
            float sx = stars[i] * engine.screenW;
            float sy = stars[i+1] * engine.screenH * 0.55f;
            float sr = 1.5f + (i % 4) * 0.5f;
            c.drawCircle(sx, sy, sr, paint);
        }

        // Đường giới hạn nguy hiểm
        int dangerRow = Grid.ROWS - 2;
        float dangerY = Grid.cellCY(dangerRow, engine.gridOffsetY, engine.cellW) + engine.cellW * 0.5f;
        paint.setColor(0x44FF4444);
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(0, dangerY, engine.screenW, dangerY + 3f, paint);
    }

    private void drawGrid(Canvas c) {
        float r = engine.cellW * 0.46f;
        int[][] cells = engine.grid.getCells();
        for (int row = 0; row < Grid.ROWS; row++) {
            int cols = Grid.colsInRow(row);
            for (int col = 0; col < cols; col++) {
                int color = cells[row][col];
                if (color == Bubble.EMPTY) continue;
                float cx = Grid.cellCX(row, col, engine.gridOffsetX, engine.cellW);
                float cy = Grid.cellCY(row, engine.gridOffsetY, engine.cellW);
                Bubble.draw(c, paint, color, cx, cy, r);
            }
        }
    }

    private void drawFalling(Canvas c) {
        float r = engine.cellW * 0.46f;
        for (float[] f : engine.fallingBubbles) {
            Bubble.draw(c, paint, (int) f[3], f[0], f[1], r);
        }
    }

    private void drawPopping(Canvas c) {
        for (float[] p : engine.poppingBubbles) {
            float phase  = p[3];                    // 0..1
            float radius = engine.cellW * 0.46f * (1f + phase * 0.5f);
            int   alpha  = (int) ((1f - phase) * 255);
            int   base   = Bubble.BUBBLE_COLORS[(int) p[2]];
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((alpha << 24) | (base & 0x00FFFFFF));
            c.drawCircle(p[0], p[1], radius, paint);
            // Vòng nổ
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor((alpha / 2 << 24) | 0xFFFFFF);
            c.drawCircle(p[0], p[1], radius * 1.3f, paint);
        }
    }

    private void drawAimLine(Canvas c) {
        if (!engine.aimVisible || engine.state != GameEngine.State.AIMING) return;
        paint.setColor(0x55FFFFFF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);

        // Vẽ đường chấm chấm nảy tường
        float x = engine.cannonX, y = engine.cannonY;
        float dx = engine.aimX - x, dy = engine.aimY - y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;
        dx /= len; dy /= len;
        float vx = dx * 12f, vy = dy * 12f;
        float r = engine.cellW * 0.46f;

        Path path = new Path();
        path.moveTo(x, y);
        for (int i = 0; i < 35; i++) {
            float nx = x + vx * 4;
            float ny = y + vy * 4;
            if (nx - r < 0) { vx = Math.abs(vx); }
            if (nx + r > engine.screenW) { vx = -Math.abs(vx); }
            if (ny < engine.gridOffsetY) break;
            if (i % 2 == 0) path.lineTo(x + vx * 4, y + vy * 4);
            else path.moveTo(x + vx * 4, y + vy * 4);
            x += vx * 4; y += vy * 4;
        }
        c.drawPath(path, paint);
    }

    private void drawBall(Canvas c) {
        if (engine.state == GameEngine.State.SHOOTING ||
            engine.state == GameEngine.State.AIMING) {
            Bubble.draw(c, paint, engine.currentColor,
                    engine.ballX, engine.ballY, engine.cellW * 0.46f);
        }
    }

    private void drawCannon(Canvas c) {
        float cx = engine.cannonX, cy = engine.cannonY;
        float w  = engine.cellW * 0.55f;
        float h  = engine.cellW * 1.1f;

        c.save();
        c.translate(cx, cy);
        // angle: 0 = phải, ta muốn 90° = lên
        float angleDeg = (float) Math.toDegrees(engine.cannonAngle);
        c.rotate(-(90f - angleDeg));   // xoay để hướng lên

        // Thân súng
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(60, 80, 120));
        RectF barrel = new RectF(-w * 0.3f, -h, w * 0.3f, 0);
        c.drawRoundRect(barrel, w * 0.15f, w * 0.15f, paint);

        // Viền súng
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(Color.rgb(100, 140, 200));
        c.drawRoundRect(barrel, w * 0.15f, w * 0.15f, paint);

        c.restore();

        // Đế súng
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(40, 60, 100));
        c.drawCircle(cx, cy, w * 0.6f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(Color.rgb(80, 120, 180));
        c.drawCircle(cx, cy, w * 0.6f, paint);

        // Bong bóng tiếp theo (bên phải súng)
        float nx = cx + engine.cellW * 1.5f;
        float ny = cy;
        Bubble.draw(c, paint, engine.nextColor, nx, ny, engine.cellW * 0.35f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xAAFFFFFF);
        paint.setTextSize(engine.cellW * 0.28f);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText("NEXT", nx, ny + engine.cellW * 0.7f, paint);
    }

    private void drawHUD(Canvas c) {
        float fs = engine.cellW * 0.38f;
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(fs);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        // Score
        paint.setColor(0xFFFFFFFF);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText("Điểm: " + engine.score, engine.cellW * 0.3f, fs * 1.4f, paint);

        // Level
        paint.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Màn " + engine.level, engine.screenW - engine.cellW * 0.3f, fs * 1.4f, paint);

        // Shot counter
        int remaining = GameEngine.SHOOT_LIMIT_PUBLIC - (engine.shotCount % GameEngine.SHOOT_LIMIT_PUBLIC);
        paint.setColor(remaining <= 5 ? Color.rgb(255, 100, 80) : Color.rgb(150, 200, 255));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(fs * 0.75f);
        c.drawText("Đẩy lưới sau " + remaining + " lần bắn", engine.screenW / 2f,
                engine.screenH - engine.cellW * 1.6f, paint);
    }
}
