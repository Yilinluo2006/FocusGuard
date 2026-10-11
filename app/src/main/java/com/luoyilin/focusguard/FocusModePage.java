package com.luoyilin.focusguard;

import android.app.Activity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import java.util.*;

final class FocusModePage {
    static final String SLEEP_RULES = "睡眠开始后不能直接提前结束。每台设备每次睡前计划最多可申请 5 次睡眠全局临时使用，睡眠全局临时次数与应用临时次数分别计数。"
            + "\n每次 2 分钟连续计时，退出 App、切换应用或锁屏也不暂停，到时自动恢复睡眠管控。"
            + "\n第五次到时后不能再临时退出；按返回键或回到桌面仍会继续管控，直到本次睡前计划的结束时间。"
            + "\n跨日计划不会在凌晨 04:00 恢复睡眠全局临时次数。电话和已设置的可用应用仍可使用；应用自身的每日额度继续生效。"
            + "\n手机和平板分别计数，离线也可申请；同步睡前计划不会合并或重置睡眠全局临时次数。";
    private final androidx.appcompat.app.AppCompatActivity activity;
    private TextView activeStatus;
    private Button endButton;
    private Switch dailySwitch;
    private Button timeButton;
    private TextView sleepProtection;
    private TextView sleepEditNotice;
    private int weekdays;
    private final Button[] weekdayButtons = new Button[7];
    private Map<String, Object> displayedPlan;

    private Map<String, Object> savedPlan() {
        Map<String, ?> values = store.prefs.getAll();
        Map<String, Object> plan = new HashMap<>();
        plan.put("daily", values.containsKey("daily") ? values.get("daily") : false);
        plan.put("minute", values.containsKey("minute") ? values.get("minute") : 1350);
        plan.put("duration", values.containsKey("duration") ? values.get("duration") : 180);
        plan.put(FocusSchedule.WEEKDAYS_KEY, values.containsKey(FocusSchedule.WEEKDAYS_KEY)
                ? values.get(FocusSchedule.WEEKDAYS_KEY) : FocusSchedule.ALL_DAYS);
        return plan;
    }

    /** Refresh restored configuration without replacing an unsaved local draft. */
    void refreshConfiguration() {
        if (displayedPlan == null) return;
        allowedButton.setText("可用应用 · " + store.allowed().size() + " 个");
        Map<String, Object> saved = savedPlan();
        boolean clean = daily == (Boolean) displayedPlan.get("daily")
                && minute == (Integer) displayedPlan.get("minute")
                && weekdays == (Integer) displayedPlan.get(FocusSchedule.WEEKDAYS_KEY)
                && sleepDuration.getText().toString().equals(String.valueOf(displayedPlan.get("duration")));
        if (saved.equals(displayedPlan) || !clean) return;
        daily = (Boolean) saved.get("daily");
        minute = (Integer) saved.get("minute");
        weekdays = (Integer) saved.get(FocusSchedule.WEEKDAYS_KEY);
        sleepDuration.setText(String.valueOf(saved.get("duration")));
        dailySwitch.setChecked(daily);
        refreshWeekdays();
        timeButton.setText(timeLabel());
        displayedPlan = saved;
    }
    void saveState(Bundle out) {
        out.putString("sleepDraftMinutes", sleepDuration.getText().toString());
        out.putBoolean("sleepDraftDaily", daily);
        out.putInt("sleepDraftStart", minute);
        out.putInt("sleepDraftWeekdays", weekdays);
    }
    void restoreState(Bundle state) {
        if (state == null || !state.containsKey("sleepDraftStart")) return;
        sleepDuration.setText(state.getString("sleepDraftMinutes"));
        daily = state.getBoolean("sleepDraftDaily");
        dailySwitch.setChecked(daily);
        weekdays = state.getInt("sleepDraftWeekdays", FocusSchedule.ALL_DAYS);
        refreshWeekdays();
        minute = state.getInt("sleepDraftStart");
        timeButton.setText(timeLabel());
    }
    FocusModePage(androidx.appcompat.app.AppCompatActivity activity) { this.activity = activity; }
    private int getColor(int id) { return activity.getColor(id); }
    private android.content.res.Resources getResources() { return activity.getResources(); }
    private <T> T getSystemService(Class<T> type) { return activity.getSystemService(type); }
    private String getPackageName() { return activity.getPackageName(); }
    private void startActivity(Intent intent) { activity.startActivity(intent); }
    void refresh() {
        if (activeStatus == null) return;
        if (sleepEditNotice != null) sleepEditNotice.setText(SleepPlanGuard.summaryText(activity));
        long now = ProtectionClock.now(activity);
        long remaining = Math.max(0, store.end(now) - now);
        Bundle timed = store.timed();
        activeStatus.setText(remaining <= 0 ? "" : timed.getBoolean("active") && timed.getBoolean("countUp")
                ? MetricText.emphasize("已专注 " + (timed.getLong("elapsed") / 60000) + " 分钟", 1.7f)
                : MetricText.emphasize("剩余 " + ((remaining + 59999) / 60000) + " 分钟", 1.7f));
        activeStatus.setVisibility(remaining > 0 ? View.VISIBLE : View.GONE);
        endButton.setText(timed.getBoolean("active") && timed.getBoolean("countUp") ? "完成专注" : "提前结束");
        endButton.setVisibility(remaining > 0 ? View.VISIBLE : View.GONE);
        long sleepStart = store.scheduledStart(now);
        boolean sleeping = sleepStart > 0;
        if (sleepProtection != null) {
            sleepProtection.setVisibility(sleeping ? View.VISIBLE : View.GONE);
            boolean healthy = ProtectionWarning.healthy(activity);
            sleepProtection.setText(healthy ? "睡前管控中" : "管控已暂停 · 点击恢复保护");
            sleepProtection.setTextColor(getColor(healthy ? R.color.success_text : R.color.danger_text));
        }
        Bundle temporary = SleepTemporaryUse.state(activity);
        endButton.setEnabled(!sleeping || SleepTemporaryUse.canRequest(temporary));
        if (sleeping) {
            endButton.setText(SleepTemporaryUse.button(temporary));
            if (sleepProtection != null && healthySleep()) sleepProtection.setText(temporary.getLong("remaining")>0
                    ? "睡眠全局临时使用 · " + RemainingTimeFormat.format(temporary.getLong("remaining")) + " 后恢复管控"
                    : !temporary.getString("message", "").isEmpty() ? temporary.getString("message") : "睡前管控中");
        }
    }
    private boolean healthySleep() {return ProtectionWarning.healthy(activity);}
    private FocusModeStore store;
    private LinearLayout content;
    private int minute;
    private boolean daily;
    private EditText sleepDuration;
    private Button allowedButton;

    View create() {
        store = new FocusModeStore(activity);
        ScrollView scroll = new ScrollView(activity);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        content.setPadding(p, p, p, p);
        content.setBackgroundColor(activity.getColor(R.color.page_background));
        scroll.addView(content);
        LinearLayout page = content;
        label("计划", 32);
        activeStatus = new TextView(activity);
        activeStatus.setTextSize(16);
        activeStatus.setTextColor(getColor(R.color.success_text));
        content.addView(activeStatus);
        content = PageUi.card(activity);
        label("专注页面应用白名单设置", 20);
        allowedButton = button("可用应用 · " + store.allowed().size() + " 个", this::chooseApps);
        secondary(allowedButton);
        PageUi.addCard(page, content);
        content = PageUi.card(activity);
        LinearLayout sleepHeading = new LinearLayout(activity);
        sleepHeading.setGravity(Gravity.CENTER_VERTICAL);
        sleepHeading.addView(PageUi.heading(activity, "睡前管控", 20), new LinearLayout.LayoutParams(0, -2, 1));
        TextView help = new TextView(activity);
        help.setText("说明"); help.setTextSize(14); help.setTextColor(getColor(R.color.accent_text));
        help.setGravity(Gravity.CENTER); help.setContentDescription("睡前管控说明");
        help.setOnClickListener(v -> showSleepRules());
        sleepHeading.addView(help, new LinearLayout.LayoutParams(dp(56), dp(48)));
        content.addView(sleepHeading);
        sleepProtection = new TextView(activity);
        sleepProtection.setTextSize(14); sleepProtection.setMinHeight(dp(48));
        sleepProtection.setGravity(Gravity.CENTER_VERTICAL);
        sleepProtection.setOnClickListener(v -> {
            if (!ProtectionWarning.healthy(activity)) startActivity(new Intent(activity, PermissionGuideActivity.class));
        });
        content.addView(sleepProtection);
        if (!getSystemService(android.app.NotificationManager.class).areNotificationsEnabled()) {
            secondary(button("开启睡前提醒", () -> startActivity(new Intent(
                    android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()))));
        }
        daily = store.prefs.getBoolean("daily", false);
        weekdays = store.prefs.getInt(FocusSchedule.WEEKDAYS_KEY, FocusSchedule.ALL_DAYS);
        Switch enabled = new Switch(activity);
        dailySwitch = enabled;
        enabled.setText("每天重复");
        enabled.setTextColor(activity.getColor(R.color.text_primary));
        enabled.setTextSize(16);
        enabled.setMinHeight(dp(48));
        enabled.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{activity.getColor(R.color.success_text), activity.getColor(R.color.text_secondary)}));
        enabled.setChecked(daily);
        enabled.setOnCheckedChangeListener((v, checked) -> {
            daily = checked;
            refreshWeekdays();
        });
        content.addView(enabled);
        LinearLayout weekdayRow = new LinearLayout(activity);
        String[] dayNames = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < dayNames.length; i++) {
            final int bit = 1 << i;
            Button day = new androidx.appcompat.widget.AppCompatButton(activity);
            weekdayButtons[i] = day;
            day.setText(dayNames[i]); day.setTextSize(16); day.setAllCaps(false);
            day.setMinWidth(0); day.setMinimumWidth(0);
            day.setPadding(0, 0, 0, 0);
            day.setContentDescription("星期" + dayNames[i]);
            day.setOnClickListener(v -> {
                weekdays ^= bit;
                refreshWeekdays();
            });
            LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (i > 0) dayParams.leftMargin = dp(4);
            weekdayRow.addView(day, dayParams);
        }
        content.addView(weekdayRow);
        refreshWeekdays();
        minute = store.prefs.getInt("minute", 1350);
        label("开始时间", 16);
        Button time = button(timeLabel(), () -> {});
        time.setTextSize(32);
        time.setTypeface(null, Typeface.BOLD);
        timeButton = time;
        secondary(time);
        time.setTextSize(32);
        time.setOnClickListener(v -> new TimePickerDialog(activity, (picker, h, m) -> {
            minute = h * 60 + m;
            time.setText(timeLabel());
        }, minute / 60, minute % 60, true).show());
        sleepDuration = number("限制时长", store.prefs.getInt("duration", 180));
        TextView ruleNotice = new TextView(activity);
        ruleNotice.setText("本机每次睡前计划有 5 次睡眠全局临时使用，与应用临时次数分别计数；用完后持续管控至计划结束。");
        ruleNotice.setTextSize(13); ruleNotice.setTextColor(getColor(R.color.text_secondary));
        ruleNotice.setPadding(0, dp(8), 0, dp(8)); content.addView(ruleNotice);
        sleepEditNotice = new TextView(activity);
        sleepEditNotice.setText(SleepPlanGuard.summaryText(activity));
        sleepEditNotice.setTextSize(13);
        sleepEditNotice.setTextColor(getColor(R.color.accent_text));
        sleepEditNotice.setPadding(0, dp(8), 0, dp(8));
        content.addView(sleepEditNotice);
        button("保存计划", () -> {
            int value = minutes(sleepDuration);
            if (daily && weekdays == 0) { toast("请至少选择一天"); return; }
            if (value == 0 || (daily && !ready())) return;
            if (store.end(ProtectionClock.now(activity)) > 0) {
                toast(store.scheduledStart(ProtectionClock.now(activity)) > 0 ? "睡眠期间不能修改计划，请等待计划结束" : "请先结束当前专注，再修改计划"); return;
            }
            boolean proposedDaily = daily;
            int proposedMinute = minute;
            int proposedWeekdays = weekdays;
            Map<String, ?> original = store.prefs.getAll();
            Map<String, Object> proposed = new HashMap<>(original);
            proposed.put("daily", proposedDaily);
            proposed.put("minute", proposedMinute);
            proposed.put("duration", value);
            proposed.put(FocusSchedule.WEEKDAYS_KEY, proposedWeekdays);
            if (!SleepPlanGuard.allow(activity, original, proposed)) return;
            boolean relaxing = SleepPlanGuard.isRelaxation(activity, original, proposed);
            Runnable save = () -> {
            if (store.end(ProtectionClock.now(activity)) > 0) {
                toast("专注已开始，未修改计划"); return;
            }
            Map<String, ?> latest = store.prefs.getAll();
            Map<String, Object> latestProposed = new HashMap<>(latest);
            latestProposed.put("daily", proposedDaily);
            latestProposed.put("minute", proposedMinute);
            latestProposed.put("duration", value);
            latestProposed.put(FocusSchedule.WEEKDAYS_KEY, proposedWeekdays);
            if (!SleepPlanGuard.allow(activity, latest, latestProposed)) return;
            boolean finalRelaxing = SleepPlanGuard.isRelaxation(activity, latest, latestProposed);
            boolean saved = store.prefs.edit().putBoolean("daily", proposedDaily).putInt("minute", proposedMinute)
                    .putInt("duration", value).putInt(FocusSchedule.WEEKDAYS_KEY, proposedWeekdays).commit();
            if (saved) displayedPlan = savedPlan();
            if (saved && finalRelaxing) StreakStore.breakToday(activity, "取消或调整睡眠计划");
            if (!saved && !SleepPlanGuard.allow(activity, store.prefs.getAll(), latestProposed)) return;
            toast(saved ? "计划已保存" : "保存失败，请重试");
            };
            Runnable confirmedSave = () -> {
                if (relaxing) StreakConfirmation.show(activity, "关闭计划或减少原有睡前管控时段。", save);
                else save.run();
            };
            if (proposedDaily) new MaterialAlertDialogBuilder(activity, R.style.HomeDialog)
                    .setTitle("确认睡前管控规则")
                    .setMessage(String.format(Locale.getDefault(), "开始时间 %02d:%02d，持续 %d 分钟。\n\n", proposedMinute / 60, proposedMinute % 60, value) + SLEEP_RULES)
                    .setNegativeButton("返回修改", null)
                    .setPositiveButton("了解并保存", (d, w) -> confirmedSave.run()).show();
            else confirmedSave.run();
        });
        Button end = button("提前结束", () -> {
            Bundle temporary = SleepTemporaryUse.state(activity);
            if (temporary.getBoolean("sleeping")) {
                new MaterialAlertDialogBuilder(activity, R.style.HomeDialog).setTitle("睡眠全局临时使用 2 分钟？")
                        .setMessage(SleepTemporaryUse.explanation(temporary))
                        .setNegativeButton("继续睡眠", null)
                        .setPositiveButton("确认使用", (d, w) -> {
                            Bundle result = SleepTemporaryUse.request(activity);
                            toast(result.getBoolean("saved") ? "睡眠全局临时使用已开始，2 分钟后恢复管控"
                                    : result.getString("message", "申请失败，请重试"));
                            refresh();
                        }).show();
            } else if (store.timed().getBoolean("active") && store.timed().getBoolean("countUp")) {
                new MaterialAlertDialogBuilder(activity, R.style.HomeDialog).setTitle("完成本次专注？")
                        .setNegativeButton("继续", null)
                        .setPositiveButton("完成", (d, w) -> {
                            toast(store.finishEarly(ProtectionClock.now(activity)) ? "已保存专注记录" : "保存失败，请重试");
                            refresh();
                        }).show();
            } else StreakConfirmation.show(activity,
                    "提前结束当前专注。", () -> {
                        if (store.end(ProtectionClock.now(activity)) > 0)
                            toast(store.finishEarly(ProtectionClock.now(activity)) ? "已结束" : "保存失败，请重试");
                        refresh();
                    });
        });
        secondary(end);
        end.setTextColor(activity.getColor(R.color.danger_text));
        end.setVisibility(store.end(ProtectionClock.now(activity)) > 0 ? View.VISIBLE : View.GONE);
        endButton = end;
        // Session actions belong to the page, rather than to the sleep settings card.
        content.removeView(end);
        PageUi.addCard(page, content);
        page.addView(end);
        displayedPlan = savedPlan();
        refresh();
        return scroll;
    }

    private void showPomodoroHistory() {
        StringBuilder text = new StringBuilder();
        try {
            org.json.JSONArray history = new org.json.JSONArray(store.prefs.getString("pomodoroHistory", "[]"));
            java.text.DateFormat format = new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            for (int i = history.length() - 1; i >= Math.max(0, history.length() - 20); i--) {
                org.json.JSONObject record = history.getJSONObject(i);
                String reason = record.getString("reason");
                String label = "completed".equals(reason) ? "正常完成"
                        : "sleep_takeover".equals(reason) ? "睡前计划开始" : "提前结束";
                text.append(format.format(new Date(record.getLong("startedAt")))).append("  ").append(label)
                        .append("\n完成 ").append(record.getInt("completedRounds")).append(" / ")
                        .append(record.getInt("rounds")).append(" 轮 · 专注 ")
                        .append(record.getLong("workMillis") / 60000).append(" 分钟\n\n");
            }
            if (text.length() == 0) text.append("暂无番茄钟记录");
        } catch (org.json.JSONException error) { text.append("记录暂时无法读取"); }
        new MaterialAlertDialogBuilder(activity, R.style.HomeDialog).setTitle("番茄钟记录")
                .setMessage(text.toString().trim()).setPositiveButton("关闭", null).show();
    }

    private void refreshWeekdays() {
        dailySwitch.setText(weekdays == FocusSchedule.ALL_DAYS ? "每天重复" : "每周重复");
        for (int i = 0; i < weekdayButtons.length; i++) {
            Button day = weekdayButtons[i];
            if (day == null) continue;
            boolean selected = (weekdays & (1 << i)) != 0;
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setCornerRadius(dp(14));
            shape.setColor(getColor(selected ? R.color.primary_button : R.color.secondary_surface));
            day.setBackgroundTintList(null);
            day.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(getColor(R.color.outline)), shape, null));
            day.setTextColor(getColor(selected ? R.color.on_primary : R.color.text_secondary));
            day.setSelected(selected);
            androidx.core.view.ViewCompat.setStateDescription(day, selected ? "已选择" : "未选择");
            day.setAlpha(daily ? 1f : 0.45f);
        }
    }

    private String timeLabel() { return String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60); }
    private boolean ready() {
        if (FocusAccessibilityService.isRunning(activity)) return true;
        toast("请先开启应用保护");
        startActivity(new Intent(activity, PermissionGuideActivity.class));
        return false;
    }
    private void chooseApps() {
        if (store.end(ProtectionClock.now(activity)) > 0) { toast("请先结束当前计时，再修改可用应用"); return; }
        new FocusAppPicker(activity, store.allowed(), selected -> {
                    if (store.end(ProtectionClock.now(activity)) > 0) { toast("计时已开始，未修改可用应用"); return; }
                    Map<String, ?> original = store.prefs.getAll();
                    Map<String, Object> proposed = new HashMap<>(original);
                    proposed.put("allowed", selected);
                    if (!SleepPlanGuard.allow(activity, original, proposed)) return;
                    if (store.prefs.edit().putStringSet("allowed", selected).commit())
                        allowedButton.setText("可用应用 · " + selected.size() + " 个");
                    else if (SleepPlanGuard.allow(activity, store.prefs.getAll(), proposed)) toast("保存失败，请重试");
                }).show();
    }
    private void label(String text, int size) {
        TextView view = new TextView(activity); view.setText(text); view.setTextSize(size);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(activity.getColor(R.color.text_primary)); view.setPadding(0, dp(6), 0, dp(10));
        androidx.core.view.ViewCompat.setAccessibilityHeading(view, true); content.addView(view);
    }
    private Button button(String text, Runnable action) {
        Button b = new RaisedButton(activity); b.setText(text); b.setAllCaps(false);
        b.setTextSize(16); b.setTextColor(activity.getColor(R.color.on_primary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8); params.bottomMargin = dp(4); b.setMinHeight(dp(48));
        b.setOnClickListener(v -> action.run()); content.addView(b, params); return b;
    }
    private EditText number(String hint, int value) {
        LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView caption = new TextView(activity); caption.setText(hint); caption.setTextSize(16);
        caption.setTextColor(activity.getColor(R.color.text_primary));
        row.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
        EditText input = new EditText(activity); input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true); input.setTextSize(30); input.setTextColor(activity.getColor(R.color.text_primary));
        input.setTypeface(null, Typeface.BOLD); input.setGravity(Gravity.CENTER);
        input.setSelectAllOnFocus(true); input.setContentDescription(hint + "，1 到 720 分钟");
        input.setBackgroundResource(R.drawable.field_rounded); input.setPadding(dp(8), dp(6), dp(8), dp(6));
        input.setMinHeight(dp(64));
        input.setText(String.valueOf(value)); row.addView(input, new LinearLayout.LayoutParams(dp(104), -2));
        TextView unit = new TextView(activity); unit.setText("分钟"); unit.setTextColor(activity.getColor(R.color.text_secondary));
        unit.setTextSize(14); unit.setPadding(dp(8), 0, 0, 0); row.addView(unit);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8); content.addView(row, params); return input;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void secondary(Button b) {
        PageUi.secondary(b);
    }
    private void showSleepRules() {
        new MaterialAlertDialogBuilder(activity, R.style.HomeDialog).setTitle("睡前管控")
                .setMessage("开启重复、选择星期并保存后，应用会在所选日的设定时间限制使用，开始前 5 分钟提醒。跨午夜的管控按开始当天计算。\n\n"
                        + SleepPlanGuard.ruleText(activity) + "\n\n" + SLEEP_RULES + "\n\n睡前计划开始时会结束正在进行的专注，并分别保存记录。")
                .setPositiveButton("知道了", null).show();
    }
    private int minutes(EditText input) {
        try { int n = Integer.parseInt(input.getText().toString()); if (n >= 1 && n <= 720) return n; }
        catch (NumberFormatException ignored) { }
        input.setError("请输入 1 到 720 分钟"); return 0;
    }
    private void toast(String text) { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show(); }
}
