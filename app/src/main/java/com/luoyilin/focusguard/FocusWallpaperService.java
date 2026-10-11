package com.luoyilin.focusguard;

import android.graphics.Canvas;
import android.graphics.Color;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

/** Opt-in experiment: a real wallpaper bound in the same process as enforcement. */
public class FocusWallpaperService extends WallpaperService {
    @Override public void onCreate() { super.onCreate(); WallpaperQuotaMonitor.acquire(this, this); ProtectionRecoveryObserver.acquire(this, this); }
    @Override public void onDestroy() { WallpaperQuotaMonitor.release(this); ProtectionRecoveryObserver.release(this); super.onDestroy(); }
    @Override
    public Engine onCreateEngine() {
        Log.i("FocusWallpaper", "Engine created pid=" + android.os.Process.myPid());
        return new FocusEngine();
    }

    private final class FocusEngine extends Engine {
        private android.graphics.Bitmap bitmap;
        private long imageVersion = Long.MIN_VALUE;
        @Override public void onDestroy() { if (bitmap != null) bitmap.recycle(); bitmap = null; super.onDestroy(); }
        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            drawWallpaper();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (visible) {
                drawWallpaper();
            }
        }

        private void drawWallpaper() {
            SurfaceHolder holder = getSurfaceHolder();
            if (!holder.getSurface().isValid()) {
                return;
            }
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    canvas.drawColor(Color.rgb(32, 58, 49));
                    java.io.File image = WallpaperGuard.image(FocusWallpaperService.this);
                    long version = image.lastModified();
                    if (version != imageVersion) {
                        if (bitmap != null) bitmap.recycle();
                        bitmap = image.exists() ? android.graphics.BitmapFactory.decodeFile(image.getAbsolutePath()) : null;
                        imageVersion = version;
                    }
                    if (bitmap != null) {
                        float scale = Math.max(canvas.getWidth() / (float)bitmap.getWidth(), canvas.getHeight() / (float)bitmap.getHeight());
                        float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
                        android.graphics.RectF dst = new android.graphics.RectF((canvas.getWidth()-w)/2, (canvas.getHeight()-h)/2,
                                (canvas.getWidth()+w)/2, (canvas.getHeight()+h)/2);
                        canvas.drawBitmap(bitmap, null, dst, new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
                    }
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }
}
