package com.luoyilin.focusguard;

import android.content.Context;
import android.os.Bundle;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** The UI, provider, sync and restore paths share the same trusted-time decision. */
final class SleepPlanGuard {
    static Map<String, Object> candidate(Map<String, ?> current, Bundle changes, boolean clear) {
        Map<String, Object> proposed = new HashMap<>();
        if (!clear) proposed.putAll(current);
        for (String key : changes.keySet()) {
            Object value = changes.get(key);
            if (value == null) proposed.remove(key);
            else proposed.put(key, value instanceof ArrayList ? new HashSet<>((ArrayList<?>) value) : value);
        }
        return proposed;
    }

    static String denial(Context context, Map<String, ?> before, Map<String, ?> after) {
        if (!SleepPlanRules.valid(after)) return "睡前计划无效，请检查时间、时长和星期设置";
        if (!SleepPlanRules.changed(before, after)) return null;
        ProtectionTimeLedger.Sample clock = ProtectionClock.read(context);
        if (Boolean.TRUE.equals(before.get("daily")) && FocusSchedule.activeStart(clock.millis,
                number(before, "minute", 1350), number(before, "duration", 180),
                number(before, "sleepWeekdays", 127), clock.zone) > 0)
            return "睡眠期间不能修改计划或可用应用，请等待本次管控结束";
        if (!SleepPlanRules.isRelaxation(before, after, clock.zone, clock.millis) || RestrictionStore.canRelax(context)) return null;
        return (!clock.verified || !clock.aligned
                ? "日期时间正在核验或与校验时间不一致。请恢复正确时间和时区，再联网核验；手动改时不能提前开放修改。\n\n" : "")
                + ruleText(context);
    }

    static String ruleText(Context context) {
        return "关闭计划、减少管控时间或天数、增加可用应用，请在以下时间操作：\n"
                + RestrictionStore.windowText(context)
                + "\n管控时区：" + ProtectionClock.zone(context).getId()
                + "\n\n其他时间可加强限制，但须保留原有受控时段。睡眠开始后请等待本次管控结束再修改。";
    }

    static String summaryText(Context context) {
        return "放宽计划仅限：" + RestrictionStore.windowText(context)
                + "\n其他时间可加强限制；改系统时间不会提前开放修改。";
    }

    static boolean isRelaxation(Context context, Map<String, ?> before, Map<String, ?> after) {
        ProtectionTimeLedger.Sample clock = ProtectionClock.read(context);
        return SleepPlanRules.isRelaxation(before, after, clock.zone, clock.millis);
    }

    static boolean allow(Context context, Map<String, ?> before, Map<String, ?> after) {
        String reason = denial(context, before, after);
        if (reason == null) return true;
        new MaterialAlertDialogBuilder(context, R.style.HomeDialog).setTitle("现在不能修改睡前管控")
                .setMessage(reason).setPositiveButton("知道了", null).show();
        return false;
    }

    private static int number(Map<String, ?> plan, String key, int fallback) {
        Object value = plan.get(key);
        return value == null ? fallback : (Integer) value;
    }
}
