package com.luoyilin.focusguard;

import android.app.AppOpsManager;
import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A read-only dashboard; loading history and system usage never blocks the UI thread. */
final class StatisticsPage {
    private static final ExecutorService READER = Executors.newSingleThreadExecutor();
    private final AppCompatActivity activity;
    private final ZoneId zone = ZoneId.systemDefault();
    private final LinearLayout body;
    private final ScrollView scroll;
    private LocalDate date;
    private LocalDate usageDate;
    private volatile int generation;
    private List<FocusStatistics.Entry> records = Collections.emptyList();
    private Map<LocalDate, Long> dailyFocus = Collections.emptyMap();
    private List<SleepControlTimeline.Session> sleepRecords = Collections.emptyList();
    private boolean sleepActive;
    private final Map<String, Integer> taskColors = new LinkedHashMap<>();
    private final Map<String, Integer> appColors = new LinkedHashMap<>();
    private String usageSource = "all";

    StatisticsPage(AppCompatActivity activity, Bundle state) {
        this.activity = activity;
        date = today();
        usageDate = date;
        if (state != null) {
            usageSource = state.getString("statisticsUsageSource", "all");
            try { date = LocalDate.parse(state.getString("statisticsDate", date.toString())); }
            catch (RuntimeException ignored) { }
            if (date.isAfter(today())) date = today();
            try { usageDate = LocalDate.parse(state.getString("statisticsUsageDate", date.toString())); }
            catch (RuntimeException ignored) { usageDate = date; }
            if (usageDate.isAfter(today())) usageDate = today();
        }
        scroll = new ScrollView(activity); scroll.setFillViewport(true);
        body = column(); body.setPadding(dp(20), dp(20), dp(20), dp(24));
        scroll.addView(body); body.addView(PageUi.heading(activity, "统计", 32));
    }
    View view() { return scroll; }
    void save(Bundle out) {
        out.putString("statisticsDate", date.toString());
        out.putString("statisticsUsageDate", usageDate.toString());
        out.putString("statisticsUsageSource", usageSource);
    }
    private LocalDate today() { return FocusStatistics.day(System.currentTimeMillis(), zone); }
    void refresh() {
        int request = ++generation;
        int previousScroll = scroll.getScrollY();
        LocalDate selectedUsage = usageDate;
        String selectedSource = usageSource;
        if (body.getChildCount() <= 1) body.addView(text("加载中…", 14, false));
        READER.execute(() -> {
            if (request != generation) return;
            List<FocusStatistics.Entry> loaded = new ArrayList<>();
            List<SleepControlTimeline.Session> loadedSleep = new ArrayList<>();
            List<AppUsageChartView.Entry> appTimes = new ArrayList<>();
            String error = null, usageNote = "";
            boolean partial = false, active = false, runningSleep = false;
            try {
                Map<String, ?> prefs = FocusPreferences.read(activity, "focus_mode");
                partial |= parse(prefs.get("focusHistory"), false, loaded);
                partial |= parse(prefs.get("pomodoroHistory"), true, loaded);
                Object rawSleep = prefs.get("sleepControlHistory");
                if (rawSleep != null) {
                    try {
                        JSONArray history = new JSONArray(rawSleep.toString());
                        for (int i = 0; i < history.length(); i++) {
                            try { loadedSleep.add(SleepControlSession.decode(history.getJSONObject(i))); }
                            catch (JSONException invalid) { partial = true; }
                        }
                    } catch (JSONException invalid) { partial = true; }
                }
                loadedSleep.sort((a, b) -> Long.compare(b.start, a.start));
                runningSleep = prefs.get("sleepControlActive") instanceof String
                        && !((String) prefs.get("sleepControlActive")).isEmpty();
                active = number(prefs.get("manualStart")) > 0 || number(prefs.get("pomodoroStart")) > 0;
                loaded.sort((a, b) -> Long.compare(b.start, a.start));
            } catch (RuntimeException failure) { error = "专注记录读取失败，请重试"; }
            try {
                AppOpsManager ops = activity.getSystemService(AppOpsManager.class);
                if (ops == null || ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(), activity.getPackageName()) != AppOpsManager.MODE_ALLOWED) {
                    usageNote = "开启使用记录权限后可查看";
                } else {
                    long from = FocusStatistics.boundary(selectedUsage, zone);
                    long to = Math.min(System.currentTimeMillis(), FocusStatistics.boundary(selectedUsage.plusDays(1), zone));
                    AppUsageTimeline timeline = AppUsageReader.readRange(activity, from, to, null);
                    if (timeline == null) usageNote = "应用使用记录暂不可用";
                    else {
                        List<Map.Entry<String, Long>> items = new ArrayList<>(timeline.totalsAt(to).entrySet());
                        items.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
                        for (Map.Entry<String, Long> item : items) {
                            if (item.getValue() <= 0 || activity.getPackageManager().getLaunchIntentForPackage(item.getKey()) == null) continue;
                            String label = item.getKey();
                            android.graphics.drawable.Drawable icon = activity.getPackageManager().getDefaultActivityIcon();
                            try {
                                android.content.pm.ApplicationInfo info = activity.getPackageManager().getApplicationInfo(item.getKey(), 0);
                                label = activity.getPackageManager().getApplicationLabel(info).toString();
                                icon = activity.getPackageManager().getApplicationIcon(info);
                            }
                            catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
                            appTimes.add(new AppUsageChartView.Entry(item.getKey(), label, item.getValue(), icon));
                        }
                        usageNote = "";
                    }
                }
            } catch (RuntimeException failure) { usageNote = "应用使用记录读取失败"; }
            if (DeviceSyncManager.enabled(activity)) {
                List<AppUsageChartView.Entry> localTimes = new ArrayList<>(appTimes);
                com.luoyilin.focusguard.network.DeviceSyncData.Usage shared = DeviceSyncManager.loadUsage(activity, selectedUsage);
                Map<String, Long> totals = new TreeMap<>();
                Map<String, String> names = new HashMap<>();
                String ownDevice = "";
                try { ownDevice = DeviceSyncManager.device(activity); } catch (Exception ignored) { }
                if (shared != null && shared.devices != null) for (com.luoyilin.focusguard.network.DeviceSyncData.Device device : shared.devices) {
                    // Use live local system records for this device; remote devices use confirmed uploads.
                    if (ownDevice.equals(device.deviceId) && usageNote.isEmpty()) continue;
                    if (!selectedSource.equals("all") && !selectedSource.equals(device.deviceType)) continue;
                    if (device.usage != null) for (com.luoyilin.focusguard.network.DeviceSyncData.Item item : device.usage) {
                        totals.merge(item.packageName, item.millis, Long::sum); names.put(item.packageName, item.name);
                    }
                }
                if (usageNote.isEmpty() && (selectedSource.equals("all") || selectedSource.equals(DeviceSyncManager.type(activity)))) for (AppUsageChartView.Entry entry : localTimes) {
                    totals.merge(entry.packageName, entry.millis, Long::sum); names.put(entry.packageName, entry.name);
                }
                appTimes.clear();
                for (Map.Entry<String, Long> entry : totals.entrySet()) {
                    android.graphics.drawable.Drawable icon;
                    try { icon = activity.getPackageManager().getApplicationIcon(entry.getKey()); }
                    catch (Exception missing) { icon = new AppLabelIcon(names.getOrDefault(entry.getKey(), entry.getKey()), AppUsageChartView.color(Math.floorMod(entry.getKey().hashCode(), 8))); }
                    appTimes.add(new AppUsageChartView.Entry(entry.getKey(), names.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue(), icon));
                }
                if (shared == null) usageNote = "另一端记录待联网更新";
                else if (shared.fromCache) usageNote = "离线 · 显示上次同步记录";
                else usageNote = "两端用时已汇总 · 未联网设备的记录待更新";
            }
            appTimes.removeIf(entry -> entry.millis < 20 * 60_000L);
            appTimes.sort((a,b) -> Long.compare(b.millis, a.millis));
            String finalError = error, finalNote = usageNote;
            boolean finalPartial = partial, finalActive = active, finalSleepActive = runningSleep;
            Map<LocalDate, Long> loadedDaily = FocusHeatmapData.totals(loaded, zone, System.currentTimeMillis());
            activity.runOnUiThread(() -> {
                if (request != generation || activity.isFinishing() || activity.isDestroyed()) return;
                records = loaded;
                dailyFocus = loadedDaily;
                sleepRecords = loadedSleep; sleepActive = finalSleepActive;
                render(appTimes, finalError, finalNote, finalPartial, finalActive);
                scroll.post(() -> { if (request == generation) scroll.scrollTo(0, previousScroll); });
            });
        });
    }
    private static long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : 0; }
    private boolean parse(Object raw, boolean pomodoro, List<FocusStatistics.Entry> target) {
        if (raw == null) return false;
        boolean skipped = false;
        try {
            JSONArray array = new JSONArray(raw.toString());
            for (int i = 0; i < array.length(); i++) {
                try {
                    JSONObject item = array.getJSONObject(i);
                    long start = item.getLong("startedAt"), end = item.getLong("endedAt");
                    long work = item.getLong(pomodoro ? "workMillis" : "elapsedMillis");
                    if (start <= 0 || end < start || work < 0) { skipped = true; continue; }
                    target.add(new FocusStatistics.Entry(item.optString("name"),
                            pomodoro ? "strict_pomodoro" : item.optString("mode"), item.optString("reason"), start, end, work));
                } catch (JSONException badRecord) { skipped = true; }
            }
        } catch (JSONException badHistory) { skipped = true; }
        return skipped;
    }
    private void render(List<AppUsageChartView.Entry> apps, String error, String usageNote, boolean partial, boolean active) {
        body.removeAllViews();
        LinearLayout title = row(); title.addView(PageUi.heading(activity, "统计", 32), new LinearLayout.LayoutParams(0, -2, 1));
        title.addView(button("说明", this::showRules)); body.addView(title);
        if (active) body.addView(text("本次专注完成后更新", 14, false));
        if (error != null) {
            LinearLayout failure = card(error); failure.addView(button("重试", this::refresh)); return;
        }
        if (partial) body.addView(text("部分记录无法读取", 14, false));
        long now = System.currentTimeMillis();
        FocusStatistics.Summary all = FocusStatistics.summarize(records, 0, now);
        LinearLayout total = card("累计专注");
        long first = records.stream().mapToLong(e -> e.start).min().orElse(now);
        long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(FocusStatistics.day(first, zone), today()) + 1);
        metrics(total, "次数", String.valueOf(all.sessions), "时长", FocusStatistics.duration(all.millis));
        total.addView(text("日均 " + FocusStatistics.duration(all.millis / days) + "  ·  完成 " + all.completed + " 次", 14, false));

        long from = FocusStatistics.boundary(date, zone), to = Math.min(now, FocusStatistics.boundary(date.plusDays(1), zone));
        FocusStatistics.Summary daily = FocusStatistics.summarize(records, from, to);
        LinearLayout day = PageUi.card(activity);
        PageUi.addCard(body, day);
        day.addView(dailyHeading());
        metrics(day, "次数", String.valueOf(daily.sessions), "时长", FocusStatistics.duration(daily.millis));
        taskDistribution(day, daily.tasks);
        renderUsage(apps, usageNote);

        renderHeatmap();

        LinearLayout hours = card("当日专注时段");
        long[] hourly = new long[24]; String[] hourLabels = new String[24];
        ZonedDateTime cursor = Instant.ofEpochMilli(from).atZone(zone);
        long dayEnd = FocusStatistics.boundary(date.plusDays(1), zone);
        while (cursor.toInstant().toEpochMilli() < dayEnd) {
            long a = cursor.toInstant().toEpochMilli(), b = Math.min(dayEnd, cursor.plusHours(1).toInstant().toEpochMilli());
            int slot = (cursor.getHour() + 20) % 24;
            hourly[slot] += FocusStatistics.summarize(records, a, Math.min(b, now)).millis;
            cursor = cursor.plusHours(1);
        }
        for (int i = 0; i < 24; i++) hourLabels[i] = String.format(Locale.ROOT, "%02d", (i + 4) % 24);
        chart(hours, hourly, hourLabels);
        long monthFrom = FocusStatistics.boundary(date.withDayOfMonth(1), zone);
        long monthTo = Math.min(now, FocusStatistics.boundary(date.withDayOfMonth(1).plusMonths(1), zone));
        ranking(card(date.getMonthValue() + " 月中断原因"), FocusStatistics.summarize(records, monthFrom, monthTo).reasons, true, "本月暂无中断记录");
        renderSleep(monthFrom, monthTo);
    }
    private void renderHeatmap() {
        LinearLayout trend = card("专注趋势");
        HorizontalScrollView weeks = new HorizontalScrollView(activity);
        weeks.setHorizontalScrollBarEnabled(false);
        FocusHeatmapView grid = new FocusHeatmapView(activity, dailyFocus, date, (day, millis) ->
                new MaterialAlertDialogBuilder(activity, R.style.HomeDialog)
                        .setTitle(day.format(DateTimeFormatter.ofPattern("yyyy年M月d日")))
                        .setMessage("专注 " + (millis > 0 && millis < 1000 ? "不足 1 秒" : FocusStatistics.duration(millis)))
                        .setPositiveButton("关闭", null).show());
        weeks.addView(grid, new android.widget.FrameLayout.LayoutParams(-2, -2));
        // Keep twelve week columns legible instead of squeezing a year into the viewport.
        weeks.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> grid.setViewportWidth(r - l));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12); trend.addView(weeks, params);
        grid.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (r - l != oldR - oldL) weeks.post(() -> weeks.fullScroll(View.FOCUS_RIGHT));
        });
        weeks.post(() -> weeks.fullScroll(View.FOCUS_RIGHT));
        LinearLayout legend = row(); legend.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        legend.addView(text("少", 12, false));
        String[] descriptions = {"无专注", "不足25分钟", "25到59分钟", "60到119分钟", "至少120分钟"};
        for (int i = 0; i < 5; i++) {
            View swatch = new View(activity);
            android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
            fill.setColor(activity.getColor(FocusHeatmapView.COLORS[i])); fill.setCornerRadius(dp(3));
            swatch.setBackground(fill); swatch.setContentDescription(descriptions[i]);
            swatch.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            LinearLayout.LayoutParams dot = new LinearLayout.LayoutParams(dp(12), dp(12));
            dot.setMarginStart(dp(5)); legend.addView(swatch, dot);
        }
        TextView more = text("多", 12, false); more.setPadding(dp(5), dp(4), 0, dp(8));
        legend.addView(more); trend.addView(legend);
    }
    private void renderUsage(List<AppUsageChartView.Entry> apps, String note) {
        LinearLayout usage = PageUi.card(activity);
        PageUi.addCard(body, usage);
        usage.addView(datedHeading("应用使用排行", usageDate, chosen -> { usageDate = chosen; refresh(); }));
        if (DeviceSyncManager.enabled(activity)) {
            LinearLayout sources = row();
            String[] keys = {"all", "phone", "tablet"}, labels = {"合计", "手机", "平板"};
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];
                Button pick = button(labels[i], () -> { usageSource = key; refresh(); });
                pick.setId(i == 0 ? R.id.statisticsUsageAll : i == 1 ? R.id.statisticsUsagePhone : R.id.statisticsUsageTablet);
                if (usageSource.equals(key)) { pick.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activity.getColor(R.color.primary_button))); pick.setTextColor(activity.getColor(R.color.on_primary)); }
                sources.addView(pick, new LinearLayout.LayoutParams(0, -2, 1));
            }
            usage.addView(sources);
        }
        if (!apps.isEmpty()) {
            int[] colors = new int[apps.size()];
            for (int i = 0; i < apps.size(); i++) {
                String pkg = apps.get(i).packageName;
                if (!appColors.containsKey(pkg)) appColors.put(pkg, AppUsageChartView.color(appColors.size()));
                colors[i] = appColors.get(pkg);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(8);
            usage.addView(new AppUsageChartView(activity, apps, colors), params);
        } else usage.addView(text("当天暂无使用满 20 分钟的应用", 14, false));
        if (!note.isEmpty()) usage.addView(text(note, 14, false));
        if (note.startsWith("开启")) usage.addView(button("开启权限", () ->
                activity.startActivity(new android.content.Intent(activity, PermissionGuideActivity.class))));
    }
    private LinearLayout dailyHeading() {
        return datedHeading(date.equals(today()) ? "今日专注" : "当日专注", date,
                chosen -> { date = chosen; refresh(); });
    }
    private LinearLayout datedHeading(String label, LocalDate selected, java.util.function.Consumer<LocalDate> change) {
        LinearLayout heading = row();
        TextView title = PageUi.heading(activity, label, 18);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.setMarginEnd(dp(4));
        heading.addView(title, titleParams);
        LinearLayout controls = row();
        Button previous = dateAction("‹", () -> change.accept(selected.minusDays(1)));
        previous.setTextSize(24); previous.setContentDescription("前一天");
        controls.addView(previous, new LinearLayout.LayoutParams(dp(48), -2));
        String format = selected.getYear() == today().getYear() ? "MM-dd" : "yy/MM/dd";
        Button pick = dateAction(selected.format(DateTimeFormatter.ofPattern(format)), () -> chooseDate(selected, change));
        pick.setTypeface(null, Typeface.BOLD);
        pick.setContentDescription(label + "，选择日期，当前 " + selected + "，凌晨四点分界");
        controls.addView(pick, new LinearLayout.LayoutParams(-2, -2));
        Button next = dateAction("›", () -> {
            if (selected.isBefore(today())) change.accept(selected.plusDays(1));
        });
        next.setTextSize(24); next.setContentDescription("后一天");
        next.setEnabled(selected.isBefore(today()));
        controls.addView(next, new LinearLayout.LayoutParams(dp(48), -2));
        heading.addView(controls, new LinearLayout.LayoutParams(-2, -2));
        return heading;
    }
    private void chooseDate(LocalDate selected, java.util.function.Consumer<LocalDate> change) {
        DatePickerDialog dialog = new DatePickerDialog(activity, (v, y, m, d) -> {
            LocalDate choice = LocalDate.of(y, m + 1, d);
            if (!choice.isAfter(today())) change.accept(choice);
        }, selected.getYear(), selected.getMonthValue() - 1, selected.getDayOfMonth());
        dialog.getDatePicker().setMaxDate(today().atStartOfDay(zone).toInstant().toEpochMilli());
        dialog.show();
    }
    private Button dateAction(String label, Runnable action) {
        Button button = new Button(activity);
        button.setText(label); button.setTextSize(14); button.setAllCaps(false);
        button.setTextColor(new android.content.res.ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{activity.getColor(R.color.text_secondary), activity.getColor(R.color.accent_text)}));
        button.setBackgroundTintList(null);
        android.util.TypedValue selectable = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true);
        button.setBackgroundResource(selectable.resourceId);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setPadding(dp(4), dp(8), dp(4), dp(8));
        button.setOnClickListener(v -> action.run());
        return button;
    }
    private void taskDistribution(LinearLayout day, Map<String, Long> tasks) {
        if (tasks.isEmpty()) {
            day.addView(text("当天还没有专注时长", 14, false));
            return;
        }
        // Assign colors from the complete history, not the selected day's ranking.
        // Visiting another day or changing the ranking keeps each task's color.
        for (int i = records.size() - 1; i >= 0; i--) {
            String name = records.get(i).name;
            if (!taskColors.containsKey(name)) taskColors.put(name, TaskPieChartView.color(taskColors.size()));
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(tasks.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        long[] values = new long[sorted.size()];
        int[] colors = new int[sorted.size()];
        String[] names = new String[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            names[i] = sorted.get(i).getKey(); values[i] = sorted.get(i).getValue();
            colors[i] = taskColors.get(names[i]);
        }
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(-1, dp(176));
        chartParams.topMargin = dp(8); chartParams.bottomMargin = dp(12);
        day.addView(new TaskPieChartView(activity, values, colors, names), chartParams);
        for (int i = 0; i < names.length; i++) {
            LinearLayout line = row();
            View dot = new View(activity);
            android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
            fill.setShape(android.graphics.drawable.GradientDrawable.OVAL); fill.setColor(colors[i]);
            dot.setBackground(fill); dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(10), dp(10));
            dotParams.setMarginEnd(dp(10)); line.addView(dot, dotParams);
            TextView name = text(names[i], 14, false);
            name.setTextColor(activity.getColor(R.color.text_primary));
            name.setMaxLines(2); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
            nameParams.setMarginEnd(dp(12)); line.addView(name, nameParams);
            line.addView(text(FocusStatistics.duration(values[i]), 14, true));
            day.addView(line);
        }
    }
    private void renderSleep(long monthFrom, long monthTo) {
        LinearLayout sleep = card("睡前应用管控 · " + date.getMonthValue() + " 月");
        int count = 0, completed = 0, interrupted = 0, disabled = 0, unavailable = 0;
        long confirmed = 0, lost = 0, unknown = 0;
        for (SleepControlTimeline.Session s : sleepRecords) {
            // Whole-night records belong to their scheduled start day, including after 04:00.
            if (s.start < monthFrom || s.start >= monthTo) continue;
            count++; confirmed += s.between(s.start, s.plannedEnd, true);
            lost += s.between(s.start, s.plannedEnd, false); unknown += s.unknown();
            if ("planned_end".equals(s.reason)) completed++;
            if ("user_interrupted".equals(s.reason)) interrupted++;
            disabled += s.disabledCount; unavailable += s.unavailableCount;
        }
        metrics(sleep, "次数", count + " 次", "保护时长", FocusStatistics.duration(confirmed));
        sleep.addView(text("按时结束 " + completed + " 次  ·  提前解除 " + interrupted + " 次", 14, false));
        if (lost > 0 || disabled > 0 || unavailable > 0)
            sleep.addView(text("保护暂停 " + FocusStatistics.duration(lost)
                    + "\n保护被关闭 " + disabled + " 次  ·  保护异常 " + unavailable + " 次", 14, false));
        if (unknown > 0) sleep.addView(text("无法确认 " + FocusStatistics.duration(unknown), 14, false));
        if (sleepActive) sleep.addView(text("本次管控结束后更新", 14, false));
        if (count == 0) sleep.addView(text("还没有管控记录", 14, false));
        sleep.addView(button("查看管控记录", this::showSleepRecords));
    }
    private void showSleepRecords() {
        StringBuilder message = new StringBuilder(); int count = 0;
        DateTimeFormatter format = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(zone);
        for (SleepControlTimeline.Session s : sleepRecords) {
            if (!FocusStatistics.day(s.start, zone).equals(date)) continue;
            if (count++ == 100) { message.append("仅显示最近 100 条\n"); break; }
            message.append("计划开始 ").append(format.format(Instant.ofEpochMilli(s.start))).append(" → 结束 ")
                    .append(format.format(Instant.ofEpochMilli(s.endedAt))).append('\n')
                    .append(SleepControlTimeline.reason(s.reason)).append(" · 计划 ")
                    .append(FocusStatistics.duration(s.plannedEnd - s.start)).append('\n')
                    .append("保护时长 ").append(FocusStatistics.duration(s.between(s.start, s.plannedEnd, true)))
                    .append(" · 未保护 ").append(FocusStatistics.duration(s.between(s.start, s.plannedEnd, false)))
                    .append(" · 无法确认 ").append(FocusStatistics.duration(s.unknown())).append('\n')
                    .append("保护被关闭 ").append(s.disabledCount).append(" 次 · 保护异常 ")
                    .append(s.unavailableCount).append(" 次\n\n");
        }
        new MaterialAlertDialogBuilder(activity, R.style.HomeDialog).setTitle(date + " 睡前管控")
                .setMessage(message.length() == 0 ? "当日暂无已结束的管控记录" : message.toString().trim())
                .setPositiveButton("关闭", null).show();
    }
    private void showRules() {
        new MaterialAlertDialogBuilder(activity, R.style.HomeDialog).setTitle("统计说明")
                .setMessage("每天凌晨 04:00 开始新的一天。专注次数记在开始当天，跨天的时长分到各天。\n\n专注结束并保存后才会显示。提前结束前的专注时间也会保留；番茄钟休息不算专注。日均包含首次记录以来没有专注的日子。\n\n睡前记录记在计划开始当天。保护时长根据服务连接状态估算，不保证每次拦截都成功；手机休眠、重启等无法确认的时间不会算成已保护。按时结束也不代表全程保护正常。只记录安装相应版本后观察到的时间，不补算更早或没有记录的时段。\n\n这里记录的是应用管控，不是实际睡眠。应用使用记录来自系统，较早的数据可能不完整。使用时保护比例为参考值，不用于连续坚持结算。")
                .setPositiveButton("知道了", null).show();
    }
    private void showRecords(long from, long to) {
        StringBuilder message = new StringBuilder(); int count = 0;
        DateTimeFormatter format = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(zone);
        for (FocusStatistics.Entry e : records) {
            if (e.between(from, to) == 0 && !(e.start >= from && e.start < to)) continue;
            if (count++ == 100) { message.append("仅显示最近 100 条\n"); break; }
            String mode = "strict_pomodoro".equals(e.mode) ? "番茄钟" : "countup".equals(e.mode) ? "正计时" : "倒计时";
            message.append(e.name).append(" · ").append(mode).append('\n')
                    .append(format.format(Instant.ofEpochMilli(e.start))).append(" · ").append(FocusStatistics.duration(e.work))
                    .append(" · ").append(FocusStatistics.reason(e.reason)).append("\n\n");
        }
        new MaterialAlertDialogBuilder(activity, R.style.HomeDialog).setTitle(date + " 专注记录")
                .setMessage(message.length() == 0 ? "当日暂无专注记录" : message.toString().trim())
                .setPositiveButton("关闭", null).show();
    }
    private void chart(LinearLayout parent, long[] values, String[] labels) {
        boolean any = false; for (long value : values) any |= value > 0;
        if (!any) { parent.addView(text("还没有专注数据", 14, false)); return; }
        parent.addView(new StatisticsChartView(activity, values, labels), new LinearLayout.LayoutParams(-1, dp(170)));
    }
    private void ranking(LinearLayout parent, Map<String, Long> values, boolean counts, String empty) {
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(values.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        if (sorted.isEmpty()) { parent.addView(text(empty, 14, false)); return; }
        long maximum = Math.max(1, sorted.get(0).getValue());
        for (Map.Entry<String, Long> item : sorted) {
            LinearLayout line = row(); TextView label = text(item.getKey(), 14, false);
            label.setMaxLines(2); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            line.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            line.addView(text(counts ? item.getValue() + " 次" : FocusStatistics.duration(item.getValue()), 14, true));
            parent.addView(line);
            ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(1000); bar.setProgress((int) (item.getValue() * 1000.0 / maximum));
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(activity.getColor(R.color.accent_text)));
            bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(activity.getColor(R.color.secondary_surface)));
            bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(5)); p.bottomMargin = dp(12); parent.addView(bar, p);
        }
    }
    private void metrics(LinearLayout parent, String a, String av, String b, String bv) {
        LinearLayout line = row();
        String[] labels = {a, b}, values = {av, bv};
        for (int i = 0; i < 2; i++) {
            LinearLayout metric = column(); metric.addView(text(labels[i], 14, false));
            TextView value = text(values[i], 24, true); value.setTextColor(activity.getColor(R.color.accent_text));
            metric.addView(value); line.addView(metric, new LinearLayout.LayoutParams(0, -2, 1));
        }
        parent.addView(line);
    }
    private LinearLayout card(String title) {
        LinearLayout card = PageUi.card(activity);
        PageUi.addCard(body, card); card.addView(PageUi.heading(activity, title, 18)); return card;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(activity); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(activity); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView text(String value, int size, boolean bold) {
        TextView v = new TextView(activity); v.setText(value); v.setTextSize(size);
        v.setTextColor(activity.getColor(bold ? R.color.text_primary : R.color.text_secondary));
        if (bold) v.setTypeface(null, Typeface.BOLD);
        v.setPadding(0, dp(4), 0, dp(8)); return v;
    }
    private Button button(String label, Runnable action) {
        Button v = new Button(activity); v.setText(label); v.setAllCaps(false); v.setTextSize(14);
        PageUi.secondary(v); v.setTextSize(14);
        v.setMinWidth(dp(48)); v.setMinimumWidth(dp(48)); v.setMinHeight(dp(48));
        v.setOnClickListener(unused -> action.run()); return v;
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
