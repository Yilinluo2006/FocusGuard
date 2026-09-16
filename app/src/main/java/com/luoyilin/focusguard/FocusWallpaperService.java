package com.luoyilin.focusguard;

import android.graphics.Canvas;
import android.graphics.Color;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

/** Opt-in experiment: a real wallpaper bound in the same process as enforcement. */
public class FocusWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        Log.i("FocusWallpaper", "Engine created pid=" + android.os.Process.myPid());
        return new FocusEngine();
    }

    private final class FocusEngine extends Engine {
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
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }
}
