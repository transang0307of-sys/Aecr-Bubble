package com.aecr.bubble;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

public class Bubble {

    public static final int EMPTY   = -1;
    public static final int COLORS  = 7;

    // Color palette giống Frozen Bubble
    public static final int[] BUBBLE_COLORS = {
        Color.rgb(220,  50,  50),   // 0 Đỏ
        Color.rgb( 50, 150, 220),   // 1 Xanh dương
        Color.rgb( 50, 200,  80),   // 2 Xanh lá
        Color.rgb(240, 200,  30),   // 3 Vàng
        Color.rgb(200,  80, 200),   // 4 Tím
        Color.rgb(240, 140,  30),   // 5 Cam
        Color.rgb( 80, 220, 220),   // 6 Xanh ngọc
    };

    public int color;       // index vào BUBBLE_COLORS, hoặc EMPTY
    public float x, y;      // vị trí tâm (pixel)
    public float vx, vy;    // vận tốc khi đang bay
    public boolean moving;
    public boolean marked;  // để xoá

    public Bubble(int color, float x, float y) {
        this.color   = color;
        this.x       = x;
        this.y       = y;
        this.moving  = false;
        this.marked  = false;
    }

    public static void draw(Canvas canvas, Paint paint, int colorIdx, float cx, float cy, float r) {
        if (colorIdx == EMPTY) return;
        int base = BUBBLE_COLORS[colorIdx];

        // Thân bong bóng
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(base);
        canvas.drawCircle(cx, cy, r, paint);

        // Viền sáng
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(r * 0.08f);
        paint.setColor(0x55FFFFFF);
        canvas.drawCircle(cx, cy, r * 0.88f, paint);

        // Điểm sáng nhỏ góc trên-trái
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99FFFFFF);
        canvas.drawCircle(cx - r * 0.28f, cy - r * 0.30f, r * 0.22f, paint);

        // Điểm sáng rất nhỏ
        paint.setColor(0xCCFFFFFF);
        canvas.drawCircle(cx - r * 0.18f, cy - r * 0.22f, r * 0.10f, paint);
    }

    public void drawSelf(Canvas canvas, Paint paint, float r) {
        draw(canvas, paint, color, x, y, r);
    }
}
