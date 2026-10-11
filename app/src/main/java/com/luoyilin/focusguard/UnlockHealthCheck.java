package com.luoyilin.focusguard;

import android.content.Context;

/** One unlock check shares the same recovery notification as boot and foreground checks. */
final class UnlockHealthCheck {
    static void run(Context context) {
        ProtectionRecovery.check(context, "unlock");
        ProtectionCapabilities capabilities = ProtectionRecovery.capabilities(context);
        boolean healthy = capabilities.daily && capabilities.sleep && capabilities.focus;
        CoverageStore.recordUnlockCheck(context, System.currentTimeMillis(), healthy);
        android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
        if (manager != null) manager.cancel(54); // Remove the previous duplicate unlock warning.
    }
}
