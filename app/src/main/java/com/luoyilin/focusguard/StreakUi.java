package com.luoyilin.focusguard;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.widget.*;
import android.view.Gravity;
import android.animation.ValueAnimator;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.time.ZoneId;
import java.util.Properties;

final class StreakUi {
    static void refresh(AppCompatActivity a) {
        ComplianceAudit.request(a, false);
        TextView status = a.findViewById(R.id.tvStreak);
        if (status == null) return;
        try {
            Properties p = StreakStore.snapshot(a);
            status.setText(MetricText.emphasize(StreakStore.current(p) + " 天", 2.7f));
            status.setContentDescription("连续坚持 " + StreakStore.current(p) + " 天，查看记录");
            status.setTextColor(a.getColor(ProtectionWarning.healthy(a)
                    ? R.color.success_text : R.color.danger_text));
            status.setOnClickListener(v -> details(a));
            long yesterday = ProtectionClock.day(a) - 1;
            if ("完成".equals(p.getProperty("result." + yesterday))
                    && StreakLedger.n(p, "shown") < yesterday) {
                // Commit before displaying, so activity recreation cannot replay the reward.
                StreakStore.access(a, values -> StreakLedger.put(values, "shown", yesterday));
                celebrate(a, p, yesterday, false);
            }
        } catch (Exception e) {
            status.setText("坚持记录暂不可用，点击重试");
            status.setOnClickListener(v -> refresh(a));
        }
    }
    private static void details(AppCompatActivity a) {
        try {
            Properties p = StreakStore.snapshot(a);
            AlertDialog.Builder builder = new AlertDialog.Builder(a).setTitle("连续坚持")
                    .setMessage("当前 " + StreakStore.current(p) + " 天 · 最长 "
                            + StreakLedger.n(p, "best") + " 天\n\n" + StreakStore.week(p)
                            + "\n\n" + StreakStore.today(a, p)
                            + "\n\n每天 04:00 为分界。当天实际使用应用限制、专注或睡前计划，并遵守所有已执行的规则，即可完成守约。"
                            + "\n应用额度每台设备独立；仅加上该应用实际获批的临时时间。无需用完额度，也无需专注满一定时长。"
                            + "\n跨日睡前计划到时后结算到开始日。合法临时使用不算中断；无法确认的记录先等待核实。"
                            + "\n开启设备同步后，已使用的设备共同确认；离线记录保留，联网后合并。")
                    .setNegativeButton("关闭", null)
                    .setPositiveButton("添加桌面小组件", (d, w) -> {
                        WidgetPin.show(a);
                    });
            if (BuildConfig.DEBUG) builder.setNeutralButton("预览庆祝效果", (d, w) -> {
                Properties demo = new Properties();
                long day = ProtectionClock.day(a) - 1;
                StreakLedger.put(demo, "streak", 7);
                StreakLedger.put(demo, "ms." + day, 14 * 3600000L + 20 * 60000L);
                celebrate(a, demo, day, true);
            });
            builder.show();
        } catch (Exception e) { Toast.makeText(a, "读取记录失败，请重试", Toast.LENGTH_SHORT).show(); }
    }
    private static void celebrate(AppCompatActivity a, Properties p, long day, boolean preview) {
        long count = StreakLedger.n(p, "streak");
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL); body.setGravity(Gravity.CENTER);
        int pad = Math.round(24 * a.getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        TextView heading = text(a, preview ? "效果预览 · 不计入记录" : "又坚持了一天", 20);
        TextView number = text(a, count + " 天", 44);
        number.setTypeface(null, android.graphics.Typeface.BOLD);
        number.setTextColor(a.getColor(R.color.success_text));
        long minutes = StreakLedger.n(p, "ms." + day) / 60000;
        String milestone = count == 3 || count == 7 || count == 14 || count == 30
                ? "\n解锁 " + count + " 天里程碑" : "";
        body.addView(heading); body.addView(number);
        android.view.View confetti = new android.view.View(a) {
            final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);
                int[] colors = {0xFF2C9B70, 0xFF3B82F6, 0xFFE2B340};
                for (int i = 0; i < 18; i++) {
                    paint.setColor(colors[i % 3]);
                    float x = getWidth() * (i + 0.5f) / 18;
                    float y = getHeight() * (0.25f + (i % 3) * 0.23f);
                    canvas.save(); canvas.rotate(i * 37, x, y);
                    canvas.drawRect(x - 3, y - 7, x + 3, y + 7, paint);
                    canvas.restore();
                }
            }
        };
        body.addView(confetti, new LinearLayout.LayoutParams(-1, pad * 2));
        body.addView(text(a, "昨日守约完成\n连续坚持，比昨天更进一步" + milestone, 16));
        AlertDialog dialog = new AlertDialog.Builder(a).setView(body)
                .setPositiveButton("继续坚持", null).create();
        dialog.setOnShowListener(d -> {
            if (ValueAnimator.areAnimatorsEnabled()) {
                ValueAnimator animation = ValueAnimator.ofInt((int)Math.max(0, count - 1), (int)count);
                animation.setDuration(800);
                animation.addUpdateListener(v -> number.setText(v.getAnimatedValue() + " 天"));
                animation.start();
                number.setScaleX(0.8f); number.setScaleY(0.8f);
                number.animate().scaleX(1).scaleY(1).setDuration(650)
                        .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
                confetti.setAlpha(0);
                confetti.setTranslationY(-pad);
                confetti.animate().alpha(1).translationY(0).setDuration(900).start();
                dialog.setOnDismissListener(x -> animation.cancel());
            }
            number.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
        });
        dialog.show();
    }
    private static TextView text(AppCompatActivity a, String value, int size) {
        TextView t = new TextView(a); t.setText(value); t.setTextSize(size);
        t.setTextColor(a.getColor(R.color.text_primary)); t.setGravity(Gravity.CENTER);
        t.setPadding(0, 12, 0, 12); return t;
    }
}
