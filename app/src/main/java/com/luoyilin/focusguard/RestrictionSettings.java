package com.luoyilin.focusguard;

import android.app.TimePickerDialog;
import android.content.Context;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

final class RestrictionSettings {
    static final String USAGE_RULES = "本机每日额度在 04:00 恢复。"+QuotaPresentation.APP_POOL
            + "\n"+QuotaPresentation.CONTINUOUS_NOTE
            + "\n本机每日额度与应用临时次数都耗尽后，该应用持续限制至次日 04:00，不能通过增加额度或取消限制立即恢复。"
            + "\n应用临时次数与睡眠全局临时次数分别计算；应用临时使用只放行指定应用，专注与睡前计划规则仍生效。";
    static void requestChange(Context context, AppInfo app, boolean selected, int minutes,
                              Runnable confirmed) {
        if (!allowChange(context, app, selected, minutes)) return;
        if (RestrictionRules.isRelaxation(app.isSelected(), app.getLimitMinutes(), selected, minutes)) {
            StreakConfirmation.show(context, selected ? "增加“" + app.getAppName() + "”的每日使用时长。"
                    : "取消“" + app.getAppName() + "”的应用限制。", () -> {
                if (allowChange(context, app, selected, minutes)) confirmed.run();
            });
        } else if (selected && app.isSelected() && minutes < app.getLimitMinutes()) {
            int previousMinutes = app.getLimitMinutes();
            String message = "“" + app.getAppName() + "”的本机每日额度将从 " + previousMinutes
                    + " 分钟缩短为 " + minutes + " 分钟。\n\n确认后立即生效，今天已经使用的时间不会清零。"
                    + "恢复原额度属于增加时长，需要在允许修改的时段操作。";
            if (app.getTodayUsageMillis() >= minutes * 60_000L)
                message += "\n\n今天已用时间达到新额度，确认后将立即限制该应用；临时使用仍按原规则执行。";
            new MaterialAlertDialogBuilder(context, R.style.HomeDialog).setTitle("确认缩短使用时间？")
                    .setMessage(message)
                    .setNegativeButton("返回修改", null)
                    .setPositiveButton("确认缩短", (d, w) -> {
                        if (allowChange(context, app, selected, minutes)) confirmed.run();
                    }).show();
        } else if (selected && !app.isSelected()) {
            new MaterialAlertDialogBuilder(context, R.style.HomeDialog).setTitle("开启“" + app.getAppName() + "”的限制？")
                    .setMessage("本机每日额度：" + minutes + " 分钟。\n\n" + USAGE_RULES)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("了解并开启", (d, w) -> {
                        if (allowChange(context, app, selected, minutes)) confirmed.run();
                    }).show();
        } else confirmed.run();
    }
    static boolean allowChange(Context context, AppInfo app, boolean selected, int minutes) {
        try {
            if (app.isSelected() && app.getLimitMinutes() > 0
                    && app.getTodayUsageMillis() >= app.getLimitMinutes() * 60_000L
                    && RestrictionStore.left(context) == 0) {
                RestrictionStore.lockExhausted(context, app.getPackageName(), app.getLimitMinutes());
            }
            String locked = RestrictionStore.lockedLimits(context).getProperty(app.getPackageName());
            if (locked != null && (!selected || minutes > Integer.parseInt(locked))) {
                new MaterialAlertDialogBuilder(context, R.style.HomeDialog).setTitle("当天限制不能解除")
                        .setMessage("该应用的本机每日额度和应用临时次数已耗尽，将持续限制至次日 04:00。届时可按修改规则调整额度。")
                        .setPositiveButton("知道了", null).show();
                return false;
            }
        } catch (java.io.IOException e) {
            Toast.makeText(context, "暂时无法核实当天限制，请稍后重试", Toast.LENGTH_LONG).show();
            return false;
        }
        if (!RestrictionRules.isRelaxation(app.isSelected(), app.getLimitMinutes(), selected, minutes)
                || RestrictionStore.canRelax(context)) return true;
        ProtectionTimeLedger.Sample clock = ProtectionClock.read(context);
        new MaterialAlertDialogBuilder(context, R.style.HomeDialog).setTitle("现在不能放宽限制")
                .setMessage((!clock.verified || !clock.aligned ? "日期时间正在核验或与校验时间不一致。请恢复正确时间和时区，再联网核验；手动改时不能提前开放修改。\n\n" : "")
                        + "增加时长或关闭限制，请在以下时间操作：\n" + RestrictionStore.windowText(context)
                        + "\n\n随时可以限制更多应用或减少时长。")
                .setPositiveButton("知道了", null).show();
        return false;
    }
    static void show(Context context) {
        new MaterialAlertDialogBuilder(context, R.style.HomeDialog).setTitle("使用规则")
                .setMessage("增加时长或关闭限制的时间：\n" + RestrictionStore.windowText(context)
                        + "\n规则时区：" + ProtectionClock.zone(context).getId()
                        + "\n\n每日 04:00 恢复本机每日额度与应用临时次数。"
                        + (DeviceSyncManager.strictQuota(context)?"\n手机与平板共用每日额度。":"\n每日额度按每台设备分别计算，离线也能使用本机剩余时间。")
                        + "\n\n" + USAGE_RULES
                        + "\n规则按校验后的时间执行；手动修改日期或时区不会提前恢复额度或开放修改。重启后时间未核实前暂停放宽限制。"
                        + "\n增加时长或关闭限制会中断连续坚持；规则内的临时使用不影响。")
                .setNegativeButton("关闭", null)
                .setPositiveButton("调整可修改时间", (d, w) ->
                    new TimePickerDialog(context, (v, h, m) ->
                        new TimePickerDialog(context, (v2, h2, m2) -> {
                            try {
                                RestrictionStore.setWindow(context, h * 60 + m, h2 * 60 + m2);
                                Toast.makeText(context, "新时间将于次日生效", Toast.LENGTH_LONG).show();
                            } catch (Exception e) {
                                Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }, 7, 0, true).show(), 6, 0, true).show()).show();
    }
}
