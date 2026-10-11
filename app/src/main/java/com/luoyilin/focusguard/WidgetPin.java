package com.luoyilin.focusguard;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Properties;

final class WidgetPin {
    static void show(AppCompatActivity activity) {
        AppWidgetManager manager = AppWidgetManager.getInstance(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding, padding, padding);
        RemoteViews preview = new RemoteViews(activity.getPackageName(), R.layout.widget_streak);
        try {
            Properties p = StreakStore.snapshot(activity);
            preview.setTextViewText(R.id.widgetDays, "连续坚持 " + StreakStore.current(p) + " 天");
            preview.setTextViewText(R.id.widgetToday, StreakStore.today(activity, p));
            StreakWidget.populateWeek(activity, preview, p);
        } catch (Exception e) {
            preview.setTextViewText(R.id.widgetDays, "连续坚持");
            preview.setTextViewText(R.id.widgetToday, "打开应用查看记录");
        }
        body.addView(inflatePreview(activity, preview, body), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(160 * activity.getResources().getDisplayMetrics().density)));
        TextView guide = new TextView(activity);
        guide.setTextSize(14);
        guide.setTextColor(activity.getColor(R.color.text_primary));
        guide.setPadding(0, padding, 0, 0);
        guide.setText("OPPO".equalsIgnoreCase(android.os.Build.MANUFACTURER)
                ? "桌面长按空白处 → 卡片 → 全部卡片 → 滑到底部打开「插件」→ 奶蛙ToDo 连续坚持。"
                : "桌面长按空白处，打开小组件列表，选择「奶蛙ToDo 连续坚持」。");
        body.addView(guide);
        TextView status = new TextView(activity);
        status.setTextSize(16);
        status.setTextColor(activity.getColor(R.color.text_primary));
        status.setPadding(0, padding, 0, 0);
        body.addView(status);
        boolean supported = manager.isRequestPinAppWidgetSupported();
        status.setText(supported ? "点击「添加到桌面」尝试直接添加；若没有弹出放置界面，请按上方步骤手动添加。"
                : "当前桌面不支持应用内添加，请按上方步骤手动添加。");
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("添加桌面小组件")
                .setView(body).setNegativeButton("关闭", null)
                .setNeutralButton("前往桌面", (d, w) -> {
                    Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
                    try { activity.startActivity(home); }
                    catch (RuntimeException e) {
                        Toast.makeText(activity, "请返回桌面，长按空白处添加小组件", Toast.LENGTH_LONG).show();
                    }
                })
                .setPositiveButton("添加到桌面", null).create();
        Handler handler = new Handler(Looper.getMainLooper());
        dialog.setOnDismissListener(d -> handler.removeCallbacksAndMessages(null));
        dialog.setOnShowListener(d -> {
            Button add = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            add.setEnabled(supported);
            add.setOnClickListener(v -> {
                handler.removeCallbacksAndMessages(null);
                long started = System.currentTimeMillis();
                Bundle extras = new Bundle();
                extras.putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview);
                Intent result = new Intent(activity, StreakWidget.class).setAction(StreakWidget.PINNED);
                PendingIntent callback = PendingIntent.getBroadcast(activity, 41, result,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                try {
                    boolean accepted = manager.requestPinAppWidget(
                            new ComponentName(activity, StreakWidget.class), extras, callback);
                    activity.getSharedPreferences("widget_pin", 0).edit()
                            .putBoolean("supported", supported).putBoolean("accepted", accepted)
                            .putLong("requested", started).apply();
                    status.setText(accepted ? "已请求系统确认，请完成桌面放置。"
                            : "桌面未接受添加请求，请从系统小组件列表添加。");
                    if (!accepted) return;
                    add.setEnabled(false);
                    handler.postDelayed(new Runnable() {
                        @Override public void run() {
                            if (!dialog.isShowing()) return;
                            long success = activity.getSharedPreferences("widget_pin", 0).getLong("success", 0);
                            if (success >= started) {
                                status.setText("已添加到桌面");
                                add.setEnabled(false);
                                return;
                            }
                            if (System.currentTimeMillis() - started >= 8000) {
                                status.setText("尚未收到添加确认。若系统没有弹出放置界面，请从系统小组件列表添加。");
                                add.setEnabled(true); add.setText("重试");
                            }
                            handler.postDelayed(this, 500);
                        }
                    }, 500);
                } catch (RuntimeException e) {
                    status.setText("无法打开系统添加界面，请从系统小组件列表添加。");
                    android.util.Log.e("FocusWidget", "Pin request failed", e);
                }
            });
        });
        dialog.show();
    }

    static android.view.View inflatePreview(android.content.Context context, RemoteViews preview,
                                            android.view.ViewGroup parent) {
        // Activity's AppCompat factory substitutes views whose methods RemoteViews cannot call.
        // Inflate with the application context, as a launcher host does, to keep framework widgets.
        return preview.apply(context.getApplicationContext(), parent);
    }
}
