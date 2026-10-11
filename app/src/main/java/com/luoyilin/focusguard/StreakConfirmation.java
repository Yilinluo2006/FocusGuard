package com.luoyilin.focusguard;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;

final class StreakConfirmation {
    static String message(Context context, String action) {
        String consequence = "继续后将中断连续坚持，今日不再计为完成；历史最高纪录仍会保留。";
        try {
            long days = StreakStore.current(StreakStore.snapshot(context));
            if (days > 0) consequence = "你已连续坚持 " + days
                    + " 天。继续后连续天数将归零，今日不再计为完成；历史最高纪录仍会保留。";
        } catch (java.io.IOException ignored) {
            // A read failure must not silently bypass the warning.
        }
        return action + "\n\n" + consequence;
    }

    static void show(Context context, String action, Runnable confirmed) {
        new AlertDialog.Builder(context).setTitle("要中断连续坚持吗？")
                .setMessage(message(context, action))
                .setNegativeButton("继续坚持", null)
                .setPositiveButton("确认中断并继续", (d, w) -> confirmed.run())
                .show();
    }
}
