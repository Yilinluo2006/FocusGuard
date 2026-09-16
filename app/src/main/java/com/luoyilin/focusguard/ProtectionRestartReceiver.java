package com.luoyilin.focusguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ProtectionRestartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        ProtectionService.start(context);
        // 收到恢复信号后，仅在无障碍权限仍开启时重新启动保护服务
    }
}
