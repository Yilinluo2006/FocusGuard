package com.luoyilin.focusguard;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.util.Properties;

public class StreakWidget extends AppWidgetProvider {
    static final String PINNED = "com.luoyilin.focusguard.WIDGET_PINNED";
    @Override public void onReceive(Context context, Intent intent) {
        if (PINNED.equals(intent.getAction())) {
            context.getSharedPreferences("widget_pin", 0).edit()
                    .putLong("success", System.currentTimeMillis()).apply();
            updateAll(context);
            android.widget.Toast.makeText(context, "小组件已添加到桌面", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        super.onReceive(context, intent);
    }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        render(context, manager, ids);
    }
    static void updateAll(Context c) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        render(c, manager, manager.getAppWidgetIds(new ComponentName(c, StreakWidget.class)));
    }
    private static void render(Context c, AppWidgetManager manager, int[] ids) {
        if (ids.length == 0) return;
        try {
            Properties p = StreakStore.snapshot(c);
            for (int id : ids) {
                RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget_streak);
                views.setTextViewText(R.id.widgetDays, "连续坚持 " + StreakStore.current(p) + " 天");
                populateWeek(c, views, p);
                Intent open = new Intent(c, ProtectionWarning.healthy(c)
                        ? MainActivity.class : PermissionGuideActivity.class);
                views.setOnClickPendingIntent(R.id.widgetRoot, PendingIntent.getActivity(c, 40, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                manager.updateAppWidget(id, views);
            }
        } catch (Exception e) {
            android.util.Log.e("FocusStreak", "Widget not refreshed", e);
        }
    }

    static void populateWeek(Context context, RemoteViews views, Properties p) {
        long today = ProtectionClock.day(context);
        boolean paused = !ProtectionWarning.healthy(context) || !ComplianceAudit.usagePermission(context);
        views.setImageViewResource(R.id.widgetMascot, paused
                ? R.drawable.streak_mascot_paused : R.drawable.streak_mascot);
        views.setTextViewText(R.id.widgetDays, "连续坚持 " + StreakStore.current(p) + " 天");
        views.setTextColor(R.id.widgetDays, android.graphics.Color.WHITE);
        views.setViewVisibility(R.id.widgetWarning, paused ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setTextViewText(R.id.widgetWarning, "点击检查必要设置");
        views.setTextViewText(R.id.widgetToday, paused ? "保护需检查 · 今日待确认" : StreakStore.today(p));
        views.setViewVisibility(R.id.widgetProgress, android.view.View.GONE);
        views.removeAllViews(R.id.widgetWeek);
        for (long day = today - 4; day <= today; day++) {
            boolean current = day == today;
            boolean done = "完成".equals(p.getProperty("result." + day));
            boolean broken = p.containsKey("broken." + day) || "未完成".equals(p.getProperty("result." + day));
            boolean pending = "待确认".equals(p.getProperty("result." + day));
            java.time.LocalDate date = java.time.LocalDate.ofEpochDay(day);
            RemoteViews cell = new RemoteViews(context.getPackageName(), R.layout.widget_streak_day);
            cell.setTextViewText(R.id.weekDayLabel, current ? "今天"
                    : String.valueOf(date.getDayOfMonth()));
            cell.setTextViewText(R.id.weekDayMark, done ? "✓" : broken ? "×" : pending ? "…" : current ? "·" : "");
            cell.setInt(R.id.weekDayMark, "setBackgroundResource", current
                    ? R.drawable.streak_day_today : done ? R.drawable.streak_day_done : R.drawable.streak_day_idle);
            cell.setTextColor(R.id.weekDayMark, android.graphics.Color.parseColor(done ? "#211D15"
                    : broken ? "#FFC4B8" : "#D8BB7C"));
            cell.setTextColor(R.id.weekDayLabel, android.graphics.Color.parseColor(current ? "#FFFFFF" : "#D8C9AA"));
            cell.setContentDescription(R.id.weekDayRoot, date.getMonthValue() + "月" + date.getDayOfMonth()
                    + "日，" + (done ? "已完成" : broken ? "已中断" : pending ? "待确认" : current ? "进行中" : "无记录"));
            views.addView(R.id.widgetWeek, cell);
        }
    }
}
