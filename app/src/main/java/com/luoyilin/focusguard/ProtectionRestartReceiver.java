package com.luoyilin.focusguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ProtectionRestartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String cause = Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ? "boot"
                : Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()) ? "update" : "restart";
        ProtectionRecovery.check(context, cause);
        PendingResult pending = goAsync();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try { ProtectionRecovery.check(context, cause); } finally { pending.finish(); }
        }, 2500);
        // 收到恢复信号后，仅在无障碍权限仍开启时重新启动保护服务
    }
}
