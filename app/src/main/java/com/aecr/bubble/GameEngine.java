package com.aecr.bubble;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Logic game thuần Java, không phụ thuộc Android.
 * GameView sẽ gọi update() và draw().
 */
public class GameEngine {

    public enum State { AIMING, SHOOTING, POPPING, DROPPING, LEVEL_CLEAR, GAME_OVER }

    // ---- Config ----
    private static final float BUBBLE_SPEED   = 18f;   // pixel/frame khi bắn
    private static final int   POP_MIN        = 3;     // số bong bóng tối thiểu để nổ
    public  static final int   SHOOT_LIMIT_PUBLIC = 25;
    private static final int   SHOOT_LIMIT    = SHOOT_LIMIT_PUBLIC;    // số lần bắn trước khi lưới đẩy xuống
    private static final float DROP_SPEED     = 14f;   // tốc độ rơi

    // ---- State ----
    public State state = State.AIMING;

    public Grid   grid;
    public int    currentColor;   // màu bong bóng đang trên nòng súng
    public int    nextColor;      // màu bong bóng tiếp theo

    // Bong bóng đang bay
    public float  ballX, ballY;
    public float  ballVX, ballVY;

    // Điểm ngắm
    public float  aimX, aimY;
    public boolean aimVisible;

    public int score;
    public int level;
    public int shotCount;         // đếm số lần bắn (để đẩy lưới)
    public int bubblesPopped;

    // Falling bubbles animation
    public List<float[]> fallingBubbles = new ArrayList<>();  // {x, y, vy, color}

    // Popping animation
    public List<float[]> poppingBubbles = new ArrayList<>();  // {cx, cy, color, phase}

    // Kích thước màn hình (set từ View)
    public float screenW, screenH;
    public float gridOffsetX, gridOffsetY;
    public float cellW;           // đường kính 1 ô

    // Cannon position
    public float cannonX, cannonY;
    public float cannonAngle;     // radian, 0 = thẳng lên

    private Random rng = new Random();

    public GameEngine() {
        grid  = new Grid();
        level = 1;
        score = 0;
    }

    /** Gọi sau khi biết screenW/H. */
    public void init(float sw, float sh) {
        screenW = sw;
        screenH = sh;

        cellW       = sw / Grid.COLS;
        gridOffsetX = 0;
        gridOffsetY = sh * 0.04f;  // lưới bắt đầu từ 4% từ trên

        cannonX = sw / 2f;
        cannonY = sh - cellW * 0.7f;

        startLevel();
    }

    private void startLevel() {
        grid.clear();
        shotCount = 0;
        bubblesPopped = 0;

        int rows  = Math.min(3 + level, 8);
        int colors= Math.min(3 + (level / 2), Bubble.COLORS);
        grid.fillRandom(rows, colors);

        pickNextColors();
        state = State.AIMING;
    }

    private void pickNextColors() {
        List<Integer> active = grid.getActiveColors();
        if (active.isEmpty()) {
            currentColor = rng.nextInt(Math.min(3 + level / 2, Bubble.COLORS));
            nextColor    = rng.nextInt(Math.min(3 + level / 2, Bubble.COLORS));
        } else {
            currentColor = active.get(rng.nextInt(active.size()));
            nextColor    = active.get(rng.nextInt(active.size()));
        }
        resetBall();
    }

    private void resetBall() {
        ballX = cannonX;
        ballY = cannonY;
        state = State.AIMING;
    }

    // ---- Input ----

    /** Người chơi chỉ hướng ngắm. */
    public void setAim(float tx, float ty) {
        if (state != State.AIMING) return;
        aimX = tx;
        aimY = ty;
        aimVisible = true;
        float dx = tx - cannonX;
        float dy = ty - cannonY;
        cannonAngle = (float) Math.atan2(-dy, dx); // dương = lên
    }

    /** Người chơi nhả ngón tay / tap -> bắn. */
    public void shoot(float tx, float ty) {
        if (state != State.AIMING) return;
        float dx = tx - cannonX;
        float dy = ty - cannonY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 10f) return;                    // tap quá gần cannon
        if (ty >= cannonY - cellW) return;        // bắn xuống -> bỏ qua
        ballVX = (dx / len) * BUBBLE_SPEED;
        ballVY = (dy / len) * BUBBLE_SPEED;
        ballX  = cannonX;
        ballY  = cannonY;
        aimVisible = false;
        state  = State.SHOOTING;
    }

    // ---- Update per frame ----

    public void update() {
        switch (state) {
            case SHOOTING:  updateShooting();  break;
            case POPPING:   updatePopping();   break;
            case DROPPING:  updateDropping();  break;
            default: break;
        }
        updateFalling();
    }

    private void updateShooting() {
        ballX += ballVX;
        ballY += ballVY;

        // Nảy tường trái/phải
        float r = cellW * 0.5f;
        if (ballX - r <= 0) {
            ballX  = r;
            ballVX = Math.abs(ballVX);
        } else if (ballX + r >= screenW) {
            ballX  = screenW - r;
            ballVX = -Math.abs(ballVX);
        }

        // Chạm trần -> gắn vào hàng 0
        if (ballY - r <= gridOffsetY) {
            attachBall(0);
            return;
        }

        // Kiểm tra va chạm với lưới
        int[] snap = grid.snapToCell(ballX, ballY, gridOffsetX, gridOffsetY, cellW);
        if (snap == null) return;
        int row = snap[0], col = snap[1];

        // Nếu ô snap đã có bong bóng -> tìm ô trống kế
        if (grid.get(row, col) != Bubble.EMPTY) {
            // Kiểm tra khoảng cách với tâm các ô kề
            for (int[] nb : grid.neighbours(row, col)) {
                if (grid.get(nb[0], nb[1]) == Bubble.EMPTY) {
                    float nx = Grid.cellCX(nb[0], nb[1], gridOffsetX, cellW);
                    float ny = Grid.cellCY(nb[0], gridOffsetY, cellW);
                    float d  = dist(ballX, ballY, nx, ny);
                    if (d < cellW * 0.85f) {
                        attachBall(nb[0]);
                        return;
                    }
                }
            }
            // va chạm trực tiếp
            attachBall(row);
            return;
        }

        // Kiểm tra va chạm gần bất kỳ bong bóng nào trên lưới
        float cx = Grid.cellCX(row, col, gridOffsetX, cellW);
        float cy = Grid.cellCY(row, gridOffsetY, cellW);
        if (dist(ballX, ballY, cx, cy) < cellW * 0.7f) {
            // Kiểm tra thêm hàng kề
            for (int[] nb : grid.neighbours(row, col)) {
                if (grid.get(nb[0], nb[1]) != Bubble.EMPTY) {
                    float nd = dist(ballX, ballY,
                            Grid.cellCX(nb[0], nb[1], gridOffsetX, cellW),
                            Grid.cellCY(nb[0], gridOffsetY, cellW));
                    if (nd < cellW * 0.9f) {
                        attachBall(row);
                        return;
                    }
                }
            }
        }
    }

    private void attachBall(int preferRow) {
        // Snap chính xác
        int[] snap = grid.snapToCell(ballX, ballY, gridOffsetX, gridOffsetY, cellW);
        int row = (snap != null) ? snap[0] : preferRow;
        int col = (snap != null) ? snap[1] : 0;

        // Đảm bảo ô trống (tìm ô trống gần nhất trong hàng đó nếu cần)
        if (grid.get(row, col) != Bubble.EMPTY) {
            boolean placed = false;
            for (int[] nb : grid.neighbours(row, col)) {
                if (grid.get(nb[0], nb[1]) == Bubble.EMPTY) {
                    row = nb[0]; col = nb[1]; placed = true; break;
                }
            }
            if (!placed) { resetToNext(); return; }
        }

        grid.set(row, col, currentColor);
        shotCount++;

        // Kiểm tra nổ
        List<int[]> cluster = grid.findCluster(row, col);
        if (cluster.size() >= POP_MIN) {
            // Tạo animation nổ
            for (int[] rc : cluster) {
                float cx2 = Grid.cellCX(rc[0], rc[1], gridOffsetX, cellW);
                float cy2 = Grid.cellCY(rc[0], gridOffsetY, cellW);
                poppingBubbles.add(new float[]{cx2, cy2, grid.get(rc[0], rc[1]), 0f});
            }
            score += cluster.size() * 10 * level;
            bubblesPopped += cluster.size();
            grid.remove(cluster);

            // Tìm bong bóng lơ lửng
            List<int[]> floating = grid.findFloating();
            if (!floating.isEmpty()) {
                for (int[] rc : floating) {
                    float cx2 = Grid.cellCX(rc[0], rc[1], gridOffsetX, cellW);
                    float cy2 = Grid.cellCY(rc[0], gridOffsetY, cellW);
                    fallingBubbles.add(new float[]{cx2, cy2, DROP_SPEED + rng.nextFloat() * 4f, grid.get(rc[0], rc[1])});
                    score += 5 * level;
                }
                grid.remove(floating);
            }
            state = State.POPPING;
        } else {
            // Không nổ -> kiểm tra thua
            checkDangerAndNext();
        }
    }

    private void updatePopping() {
        boolean done = true;
        for (float[] p : poppingBubbles) {
            p[3] += 0.12f;   // phase 0..1
            if (p[3] < 1f) done = false;
        }
        if (done) {
            poppingBubbles.clear();
            checkDangerAndNext();
        }
    }

    private void updateDropping() {
        boolean done = fallingBubbles.isEmpty();
        state = done ? State.AIMING : State.DROPPING;
        if (done) resetToNext();
    }

    private void updateFalling() {
        List<float[]> remove = new ArrayList<>();
        for (float[] f : fallingBubbles) {
            f[1] += f[2];   // y += vy
            f[2] += 0.8f;   // gravity
            if (f[1] > screenH + cellW) remove.add(f);
        }
        fallingBubbles.removeAll(remove);
    }

    private void checkDangerAndNext() {
        // Thắng màn
        if (grid.isEmpty()) {
            state = State.LEVEL_CLEAR;
            score += 500 * level;
            return;
        }
        // Thua: bong bóng chạm đến vùng nguy hiểm
        int dangerRow = Grid.ROWS - 2;
        if (grid.reachedRow(dangerRow)) {
            state = State.GAME_OVER;
            return;
        }
        // Đẩy lưới xuống sau N lần bắn
        if (shotCount % SHOOT_LIMIT == 0) {
            int colors = Math.min(3 + level / 2, Bubble.COLORS);
            if (!grid.pushNewRow(colors)) {
                state = State.GAME_OVER;
                return;
            }
        }
        resetToNext();
    }

    private void resetToNext() {
        currentColor = nextColor;
        List<Integer> active = grid.getActiveColors();
        if (active.isEmpty())
            nextColor = rng.nextInt(Math.min(3 + level / 2, Bubble.COLORS));
        else
            nextColor = active.get(rng.nextInt(active.size()));
        ballX = cannonX;
        ballY = cannonY;
        state = State.AIMING;
    }

    public void nextLevel() {
        level++;
        startLevel();
    }

    public void restart() {
        level = 1;
        score = 0;
        startLevel();
    }

    private float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
