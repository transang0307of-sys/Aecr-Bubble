package com.aecr.bubble;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * Lưới bong bóng dạng hex-offset.
 * Hàng chẵn: thụt vào nửa ô (odd-r offset).
 */
public class Grid {

    public static final int COLS = 8;
    public static final int ROWS = 12;   // hàng hiển thị tối đa

    private int[][] cells;               // màu (EMPTY = -1)
    private int activeRows;              // số hàng có bong bóng
    private Random rng;

    public Grid() {
        cells = new int[ROWS][COLS];
        rng   = new Random();
        clear();
    }

    public void clear() {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                cells[r][c] = Bubble.EMPTY;
        activeRows = 0;
    }

    /** Điền N hàng ngẫu nhiên từ trên xuống, dùng K màu. */
    public void fillRandom(int numRows, int numColors) {
        activeRows = numRows;
        numColors  = Math.min(numColors, Bubble.COLORS);
        for (int r = 0; r < numRows; r++) {
            int cols = colsInRow(r);
            for (int c = 0; c < cols; c++) {
                cells[r][c] = rng.nextInt(numColors);
            }
        }
    }

    /** Thêm 1 hàng mới lên trên (đẩy các hàng cũ xuống). */
    public boolean pushNewRow(int numColors) {
        // kiểm tra nếu hàng cuối đã có bong bóng -> thua
        for (int c = 0; c < colsInRow(ROWS - 1); c++) {
            if (cells[ROWS - 1][c] != Bubble.EMPTY) return false;
        }
        // shift xuống
        for (int r = ROWS - 1; r > 0; r--)
            cells[r] = cells[r - 1].clone();
        // hàng 0 mới
        numColors = Math.min(numColors, Bubble.COLORS);
        int cols = colsInRow(0);
        for (int c = 0; c < cols; c++)
            cells[0][c] = rng.nextInt(numColors);
        activeRows = Math.min(activeRows + 1, ROWS);
        return true;
    }

    public int get(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= colsInRow(row)) return Bubble.EMPTY;
        return cells[row][col];
    }

    public void set(int row, int col, int color) {
        if (row < 0 || row >= ROWS || col < 0 || col >= colsInRow(row)) return;
        cells[row][col] = color;
        if (color != Bubble.EMPTY)
            activeRows = Math.max(activeRows, row + 1);
    }

    /** Số cột ở hàng r (hàng lẻ có thể bị 1 cột cuối empty). */
    public static int colsInRow(int row) {
        return (row % 2 == 0) ? COLS : COLS - 1;
    }

    /**
     * Tọa độ pixel tâm ô (row, col).
     * offsetX/offsetY: góc trên-trái của lưới.
     * cellW: đường kính 1 ô.
     */
    public static float cellCX(int row, int col, float offsetX, float cellW) {
        float shift = (row % 2 == 1) ? cellW * 0.5f : 0f;
        return offsetX + shift + col * cellW + cellW * 0.5f;
    }

    public static float cellCY(int row, float offsetY, float cellW) {
        // chiều cao hex = cellW * sqrt(3)/2 ≈ cellW * 0.866
        return offsetY + row * cellW * 0.866f + cellW * 0.5f;
    }

    /**
     * Snap tọa độ pixel (px, py) về ô gần nhất.
     * Trả về {row, col} hoặc null nếu ngoài lưới.
     */
    public int[] snapToCell(float px, float py, float offsetX, float offsetY, float cellW) {
        // ước tính hàng
        float rowF = (py - offsetY - cellW * 0.5f) / (cellW * 0.866f);
        int row = Math.round(rowF);
        if (row < 0) row = 0;
        if (row >= ROWS) row = ROWS - 1;

        float shift = (row % 2 == 1) ? cellW * 0.5f : 0f;
        float colF  = (px - offsetX - shift - cellW * 0.5f) / cellW;
        int   col   = Math.round(colF);
        int   cols  = colsInRow(row);
        if (col < 0)    col = 0;
        if (col >= cols) col = cols - 1;

        // thử hàng lân cận (row-1, row+1) để chọn ô gần nhất
        int bestRow = row, bestCol = col;
        float bestDist = dist2(px, py, cellCX(row, col, offsetX, cellW), cellCY(row, offsetY, cellW));

        for (int dr = -1; dr <= 1; dr++) {
            int r2 = row + dr;
            if (r2 < 0 || r2 >= ROWS) continue;
            float sh2   = (r2 % 2 == 1) ? cellW * 0.5f : 0f;
            float colF2 = (px - offsetX - sh2 - cellW * 0.5f) / cellW;
            int c2 = Math.round(colF2);
            int cols2 = colsInRow(r2);
            if (c2 < 0) c2 = 0;
            if (c2 >= cols2) c2 = cols2 - 1;
            float d = dist2(px, py, cellCX(r2, c2, offsetX, cellW), cellCY(r2, offsetY, cellW));
            if (d < bestDist) { bestDist = d; bestRow = r2; bestCol = c2; }
        }
        return new int[]{bestRow, bestCol};
    }

    private float dist2(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return dx * dx + dy * dy;
    }

    // ---- Neighbours hex ----
    private static final int[][][] NEIGHBOURS = {
        // hàng chẵn
        {{-1,-1},{-1,0},{0,-1},{0,1},{1,-1},{1,0}},
        // hàng lẻ
        {{-1,0},{-1,1},{0,-1},{0,1},{1,0},{1,1}}
    };

    public List<int[]> neighbours(int row, int col) {
        List<int[]> list = new ArrayList<>();
        int parity = row % 2;
        for (int[] d : NEIGHBOURS[parity]) {
            int nr = row + d[0], nc = col + d[1];
            if (nr >= 0 && nr < ROWS && nc >= 0 && nc < colsInRow(nr))
                list.add(new int[]{nr, nc});
        }
        return list;
    }

    /**
     * BFS: tìm tất cả ô cùng màu kết nối với (row,col).
     * Trả về list {row,col}. Nếu size >= 3 -> nổ.
     */
    public List<int[]> findCluster(int row, int col) {
        int target = get(row, col);
        if (target == Bubble.EMPTY) return new ArrayList<>();
        boolean[][] visited = new boolean[ROWS][COLS];
        List<int[]> cluster = new ArrayList<>();
        Queue<int[]> queue  = new LinkedList<>();
        queue.add(new int[]{row, col});
        visited[row][col] = true;
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            cluster.add(cur);
            for (int[] nb : neighbours(cur[0], cur[1])) {
                if (!visited[nb[0]][nb[1]] && get(nb[0], nb[1]) == target) {
                    visited[nb[0]][nb[1]] = true;
                    queue.add(nb);
                }
            }
        }
        return cluster;
    }

    /**
     * Tìm tất cả bong bóng KHÔNG kết nối với hàng 0 (sẽ rơi).
     */
    public List<int[]> findFloating() {
        boolean[][] connected = new boolean[ROWS][COLS];
        Queue<int[]> queue = new LinkedList<>();
        // seed: tất cả bong bóng hàng 0
        for (int c = 0; c < colsInRow(0); c++) {
            if (cells[0][c] != Bubble.EMPTY) {
                connected[0][c] = true;
                queue.add(new int[]{0, c});
            }
        }
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int[] nb : neighbours(cur[0], cur[1])) {
                if (!connected[nb[0]][nb[1]] && get(nb[0], nb[1]) != Bubble.EMPTY) {
                    connected[nb[0]][nb[1]] = true;
                    queue.add(nb);
                }
            }
        }
        List<int[]> floating = new ArrayList<>();
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < colsInRow(r); c++)
                if (cells[r][c] != Bubble.EMPTY && !connected[r][c])
                    floating.add(new int[]{r, c});
        return floating;
    }

    /** Xoá danh sách ô. */
    public void remove(List<int[]> cells_) {
        for (int[] rc : cells_)
            cells[rc[0]][rc[1]] = Bubble.EMPTY;
    }

    /** Kiểm tra lưới trống hoàn toàn. */
    public boolean isEmpty() {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < colsInRow(r); c++)
                if (cells[r][c] != Bubble.EMPTY) return false;
        return true;
    }

    /** Kiểm tra bong bóng chạm đến hàng nguy hiểm (>= dangerRow). */
    public boolean reachedRow(int dangerRow) {
        for (int c = 0; c < colsInRow(dangerRow); c++)
            if (cells[dangerRow][c] != Bubble.EMPTY) return true;
        return false;
    }

    /** Lấy danh sách màu đang có trên lưới (để random bong bóng tiếp theo). */
    public List<Integer> getActiveColors() {
        List<Integer> colors = new ArrayList<>();
        boolean[] seen = new boolean[Bubble.COLORS];
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < colsInRow(r); c++)
                if (cells[r][c] != Bubble.EMPTY && !seen[cells[r][c]]) {
                    seen[cells[r][c]] = true;
                    colors.add(cells[r][c]);
                }
        return colors;
    }

    public int[][] getCells() { return cells; }
    public int getActiveRows() { return activeRows; }
}
