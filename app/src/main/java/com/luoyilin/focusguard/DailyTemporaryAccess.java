package com.luoyilin.focusguard;

import android.content.Context;
import android.content.Intent;

/** Shared eligibility for requesting and consuming daily app grants. */
final class DailyTemporaryAccess {
    static boolean allowed(Context c, String pkg) {
        FocusModeStore store = new FocusModeStore(c);
        android.os.Bundle sleep = SleepTemporaryUse.state(c);
        boolean controlling = SleepTemporaryPolicy.controlling(store.end(ProtectionClock.now(c)), sleep.getLong("remaining"));
        boolean whitelisted = pkg != null && store.allowed().contains(pkg);
        if (whitelisted && sleep.getBoolean("sleeping")) {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (android.content.pm.ResolveInfo app : c.getPackageManager().queryIntentActivities(home, 0)) {
                if (pkg.equals(app.activityInfo.packageName)) { whitelisted = false; break; }
            }
        }
        return FocusLimitPolicy.useTemporaryRelease(controlling, sleep.getBoolean("sleeping"), whitelisted);
    }
}
