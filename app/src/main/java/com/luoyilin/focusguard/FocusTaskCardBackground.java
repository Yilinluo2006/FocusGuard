package com.luoyilin.focusguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import androidx.core.graphics.ColorUtils;

/** Matte task panel with a localized top-right glow and a matching fine outline. */
final class FocusTaskCardBackground extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF surface = new RectF();
    private final RectF rim = new RectF();
    private final int background;
    private final int border;
    private final int accent;
    private final int glowAlpha;
    private final float radius;
    private final float stroke;
    private Shader glow;
    private Shader edge;
    private int opacity = 255;

    FocusTaskCardBackground(Context context, int accent, float radius) {
        background = context.getColor(R.color.focus_card_surface);
        border = context.getColor(R.color.focus_card_border);
        glowAlpha = context.getResources().getInteger(R.integer.focus_card_glow_alpha);
        this.accent = accent;
        this.radius = radius;
        stroke = context.getResources().getDisplayMetrics().density * .7f;
    }

    @Override protected void onBoundsChange(Rect bounds) {
        surface.set(bounds);
        if (surface.isEmpty()) return;
        clip.reset();
        clip.addRoundRect(surface, radius, radius, Path.Direction.CW);
        rim.set(surface);
        rim.inset(stroke / 2, stroke / 2);
        glow = new RadialGradient(surface.right, surface.top, surface.width() * 1.05f,
                new int[]{ColorUtils.setAlphaComponent(accent, glowAlpha),
                        ColorUtils.setAlphaComponent(accent, glowAlpha / 3),
                        ColorUtils.setAlphaComponent(accent, 0)},
                new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP);
        edge = new LinearGradient(surface.left, surface.bottom, surface.right, surface.top,
                new int[]{border, border, accent}, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override public void draw(Canvas canvas) {
        if (surface.isEmpty() || glow == null) return;
        int save = opacity == 255 ? canvas.save() : canvas.saveLayerAlpha(surface, opacity);
        canvas.clipPath(clip);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(background);
        canvas.drawRect(surface, paint);
        paint.setColor(android.graphics.Color.WHITE);
        paint.setShader(glow);
        canvas.drawRect(surface, paint);
        paint.setShader(edge);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        float corner = Math.max(0, radius - stroke / 2);
        canvas.drawRoundRect(rim, corner, corner, paint);
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) { opacity = alpha; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
