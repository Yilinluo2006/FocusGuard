package com.luoyilin.focusguard;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import com.luoyilin.focusguard.network.RetrofitClient;

import java.util.List;

public class FocusGuardApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 先执行 Android Application 原本的初始化逻辑

        if (isMainProcess()) {
            LimitSnapshot.publish(this);
            RetrofitClient.initialize(this);
            // :guard 只负责限制服务，不初始化网络栈，降低常驻进程内存。
        }
    }

    private boolean isMainProcess() {
        String processName;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            processName = Application.getProcessName();
        } else {
            processName = findLegacyProcessName();
        }

        return getPackageName().equals(processName);
    }

    private String findLegacyProcessName() {
        ActivityManager manager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        if (manager == null) {
            return null;
        }

        List<ActivityManager.RunningAppProcessInfo> processes =
                manager.getRunningAppProcesses();

        if (processes == null) {
            return null;
        }

        int pid = Process.myPid();
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.pid == pid) {
                return process.processName;
            }
        }

        return null;
    }
}
