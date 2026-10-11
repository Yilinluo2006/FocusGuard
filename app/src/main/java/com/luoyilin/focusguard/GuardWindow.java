package com.luoyilin.focusguard;

import android.content.Context;
import android.provider.Settings;
import android.view.WindowManager;

/** User-authorized compatibility path when an OEM hides accessibility overlays. */
final class GuardWindow {
    private GuardWindow() { }

    static int type(Context context) {
        return Settings.canDrawOverlays(context)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    }

    static WindowManager manager(Context context) {
        // AccessibilityService's manager carries a token valid only for its own overlay type.
        Context owner = Settings.canDrawOverlays(context) ? context.getApplicationContext() : context;
        return owner.getSystemService(WindowManager.class);
    }
}
