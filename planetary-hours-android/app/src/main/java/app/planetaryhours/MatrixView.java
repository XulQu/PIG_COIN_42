package app.planetaryhours;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** On-screen preview of the round 25x25 Glyph Matrix. */
final class MatrixView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean[] dots = new boolean[Glyphs.SIZE * Glyphs.SIZE];

    MatrixView(Context context) {
        super(context);
    }

    void setDots(boolean[] dots) {
        this.dots = dots;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int size = Math.min(MeasureSpec.getSize(widthSpec),
                (int) (260 * getResources().getDisplayMetrics().density));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cell = getWidth() / (float) Glyphs.SIZE;
        float centre = (Glyphs.SIZE - 1) / 2f;
        for (int y = 0; y < Glyphs.SIZE; y++) {
            for (int x = 0; x < Glyphs.SIZE; x++) {
                // The matrix is a disc, so the corners have no LEDs
                float dx = x - centre;
                float dy = y - centre;
                if (dx * dx + dy * dy > 12.6f * 12.6f) continue;
                paint.setColor(dots[y * Glyphs.SIZE + x] ? 0xFFFFFFFF : 0xFF2A2A2A);
                canvas.drawCircle((x + 0.5f) * cell, (y + 0.5f) * cell, cell * 0.4f, paint);
            }
        }
    }
}
