package com.luoyilin.focusguard;

import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** The single home entry for reusable focus sessions. */
final class HomeFocus {
    private final AppCompatActivity activity;
    private final FocusModeStore sessions;
    private final FocusTaskStore tasks;
    private final LinearLayout root;
    private final LinearLayout taskList;
    private final TextView active;
    private final TextView date;
    private final TextView taskCount;
    private int taskColumns;
    private AlertDialog startConfirmation;

    HomeFocus(AppCompatActivity activity) {
        this.activity = activity;
        sessions = new FocusModeStore(activity);
        tasks = new FocusTaskStore(activity);
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        FrameLayout header = new FrameLayout(activity);
        LinearLayout headings = new LinearLayout(activity);
        headings.setOrientation(LinearLayout.VERTICAL);
        date = label("", 14, R.color.text_secondary);
        headings.addView(date);
        TextView heading = label("今日专注", 32, R.color.text_primary);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, dp(8), 0, dp(24));
        androidx.core.view.ViewCompat.setAccessibilityHeading(heading, true);
        headings.addView(heading);
        FrameLayout.LayoutParams headingParams = new FrameLayout.LayoutParams(-1, -2);
        headingParams.setMarginEnd(dp(56));
        header.addView(headings, headingParams);
        ImageButton themeToggle = new ImageButton(activity);
        themeToggle.setId(R.id.homeThemeToggle);
        boolean dark = Appearance.dark(activity);
        themeToggle.setImageResource(dark ? R.drawable.ic_theme_sun : R.drawable.ic_theme_moon);
        themeToggle.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.text_primary)));
        themeToggle.setContentDescription(dark ? "切换到浅色模式" : "切换到暗色模式");
        themeToggle.setBackgroundResource(R.drawable.home_theme_toggle_background);
        themeToggle.setPadding(dp(12), dp(12), dp(12), dp(12));
        themeToggle.setOnClickListener(view -> ThemeReveal.toggle(activity, view));
        FrameLayout.LayoutParams toggleParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
        header.addView(themeToggle, toggleParams);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        active = label("", 15, R.color.accent_text);
        active.setPadding(dp(16), dp(12), dp(16), dp(12));
        active.setMinHeight(dp(48));
        active.setGravity(Gravity.CENTER_VERTICAL);
        active.setBackgroundResource(R.drawable.home_status_background);
        active.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        active.setVisibility(View.GONE);
        LinearLayout.LayoutParams activeParams = new LinearLayout.LayoutParams(-1, -2);
        activeParams.bottomMargin = dp(16);
        root.addView(active, activeParams);
        RaisedButton create = new RaisedButton(activity);
        create.setText("+  新建专注");
        create.setTextSize(16);
        create.setTypeface(null, Typeface.BOLD);
        create.setMinHeight(dp(56));
        create.setOnClickListener(v -> edit(null));
        root.addView(create, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout section = new LinearLayout(activity);
        section.setGravity(Gravity.CENTER_VERTICAL);
        section.setPadding(0, dp(24), 0, dp(2));
        TextView title = label("专注任务", 18, R.color.text_primary);
        title.setTypeface(null, Typeface.BOLD);
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        section.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        taskCount = label("", 14, R.color.text_secondary);
        section.addView(taskCount);
        root.addView(section);
        taskList = new LinearLayout(activity);
        taskList.setOrientation(LinearLayout.VERTICAL);
        root.addView(taskList);
        root.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft && columnsForWidth(right - left) != taskColumns) {
                renderTasks();
            }
        });
        refresh();
    }

    View view() { return root; }

    void refresh() {
        date.setText(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern(
                "M月d日 EEEE", java.util.Locale.SIMPLIFIED_CHINESE)));
        refreshActive();
        renderTasks();
    }

    private int columnsForWidth(int width) {
        float scale = Math.max(1f, activity.getResources().getConfiguration().fontScale);
        if (width >= dp(560) * scale) return Math.min(4, Math.max(2, (int) (width / (dp(180) * scale))));
        return width >= dp(304) * scale ? 2 : 1;
    }

    private void renderTasks() {
        taskList.removeAllViews();
        java.util.List<FocusTaskStore.Task> saved = tasks.all();
        taskCount.setText(saved.size() + " 个");
        int width = root.getWidth() > 0 ? root.getWidth()
                : dp(activity.getResources().getConfiguration().screenWidthDp - 40);
        taskColumns = columnsForWidth(width);
        if (saved.isEmpty()) showEmpty();
        for (int i = 0; i < saved.size(); i += taskColumns) {
            LinearLayout row = new LinearLayout(activity);
            row.setBaselineAligned(false);
            for (int column = 0; column < taskColumns; column++) {
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
                if (column > 0) cell.setMarginStart(dp(12));
                int index = i + column;
                View view = index < saved.size() ? taskCard(saved.get(index)) : new Space(activity);
                row.addView(view, cell);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(12);
            taskList.addView(row, params);
        }
    }

    private MaterialCardView taskCard(FocusTaskStore.Task task) {
        boolean countup = FocusTaskStore.COUNTUP.equals(task.mode);
        boolean pomodoro = FocusTaskStore.POMODORO.equals(task.mode);
        int accent = activity.getColor(countup ? R.color.focus_card_green
                : pomodoro ? R.color.focus_card_coral : R.color.focus_card_blue);
        MaterialCardView card = card();
        card.setCardBackgroundColor(activity.getColor(R.color.focus_card_surface));
        card.setRadius(dp(20));
        card.setCardElevation(dp(3));
        card.setRippleColor(ColorStateList.valueOf(activity.getColor(R.color.focus_card_ripple)));
        card.setClipToOutline(true);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackground(new FocusTaskCardBackground(activity, accent, dp(20)));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), 0, 0, 0);
        TextView mode = label(countup ? "正计时" : pomodoro ? "番茄钟" : "倒计时",
                12, R.color.focus_card_secondary);
        header.addView(mode, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton more = new ImageButton(activity);
        more.setImageResource(R.drawable.ic_home_more);
        more.setPadding(dp(15), dp(15), dp(15), dp(15));
        more.setContentDescription("管理" + task.name);
        android.util.TypedValue selectable = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, selectable, true);
        more.setBackgroundResource(selectable.resourceId);
        more.setColorFilter(activity.getColor(R.color.focus_card_secondary));
        more.setOnClickListener(v -> manage(task));
        header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(header);

        LinearLayout center = new LinearLayout(activity);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(dp(14), dp(6), dp(14), dp(18));
        center.setMinimumHeight(dp(96));
        TextView name = label(task.name, taskColumns == 2 ? 18 : 20, R.color.focus_card_text);
        name.setTypeface(null, Typeface.BOLD);
        name.setMinLines(2);
        name.setMaxLines(2);
        name.setGravity(Gravity.CENTER);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        center.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView amount = label(countup ? "不限时" : task.value + (pomodoro ? " 轮" : " 分钟"),
                14, R.color.focus_card_secondary);
        amount.setGravity(Gravity.CENTER);
        amount.setPadding(0, dp(4), 0, 0);
        center.addView(amount, new LinearLayout.LayoutParams(-1, -2));
        content.addView(center, new LinearLayout.LayoutParams(-1, 0, 1));

        View divider = new View(activity);
        divider.setBackgroundColor(activity.getColor(R.color.focus_card_border));
        content.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout footer = new LinearLayout(activity);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(14), dp(8), dp(12), dp(8));
        footer.setMinimumHeight(dp(56));
        footer.setBackgroundColor(activity.getColor(R.color.focus_card_surface));
        ImageView timer = new ImageView(activity);
        timer.setImageResource(R.drawable.ic_home_timer);
        timer.setImageTintList(ColorStateList.valueOf(accent));
        timer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        footer.addView(timer, new LinearLayout.LayoutParams(dp(20), dp(20)));
        footer.addView(new Space(activity), new LinearLayout.LayoutParams(0, 1, 1));
        TextView begin = label("开始专注", 13, R.color.focus_card_text);
        begin.setGravity(Gravity.CENTER);
        begin.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        begin.setPadding(dp(12), dp(7), dp(12), dp(7));
        begin.setMinHeight(dp(36));
        begin.setBackground(roundedFill(activity.getColor(R.color.focus_card_action), 20));
        begin.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        footer.addView(begin, new LinearLayout.LayoutParams(-2, -2));
        content.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        card.addView(content, new FrameLayout.LayoutParams(-1, -2));
        card.setOnClickListener(v -> start(task));
        card.setContentDescription(task.name + "，" + task.detail() + "，点击确认开始");
        return card;
    }

    private GradientDrawable roundedFill(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(activity);
        card.setRadius(activity.getResources().getDimension(R.dimen.home_card_radius));
        card.setCardBackgroundColor(activity.getColor(R.color.surface_background));
        card.setStrokeWidth(0);
        card.setCardElevation(0);
        card.setRippleColor(ColorStateList.valueOf(activity.getColor(R.color.secondary_surface)));
        return card;
    }

    private void showEmpty() {
        MaterialCardView card = card();
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(24), dp(32), dp(24), dp(32));
        ImageView timer = new ImageView(activity);
        timer.setImageResource(R.drawable.ic_home_timer);
        timer.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.accent_text)));
        timer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        content.addView(timer, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = label("留一段时间给自己", 18, R.color.text_primary);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(16), 0, dp(8));
        content.addView(title);
        TextView hint = label("新建任务，之后可以重复开始", 14, R.color.text_secondary);
        hint.setGravity(Gravity.CENTER);
        content.addView(hint);
        card.addView(content, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        taskList.addView(card, params);
    }

    void refreshActive() {
        android.os.Bundle timed = sessions.timed();
        android.os.Bundle pomodoro = sessions.pomodoro();
        String current = timed.getBoolean("active") ? timed.getString("name", "")
                : pomodoro.getBoolean("active") ? pomodoro.getString("name", "") : "";
        active.setText(current.isEmpty() ? "" : "正在专注 · " + current);
        active.setVisibility(current.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void start(FocusTaskStore.Task task) {
        if (startConfirmation != null && startConfirmation.isShowing()) return;
        String detail = task.detail();
        if (FocusTaskStore.COUNTUP.equals(task.mode)) detail += " · 手动完成";
        else if (FocusTaskStore.POMODORO.equals(task.mode)) detail += "\n每轮专注 25 分钟，轮间休息 5 分钟";
        startConfirmation = new MaterialAlertDialogBuilder(activity, R.style.HomeDialog)
                .setTitle("确认开始专注？")
                .setMessage(task.name + "\n" + detail
                        + "\n\n开始后将启用专注管控，非白名单应用会受到限制。请确认已准备好，再开始专注。")
                .setNegativeButton("暂不开始", null)
                .setPositiveButton("确认开始", (dialog, which) -> begin(task))
                .create();
        startConfirmation.setOnDismissListener(dialog -> startConfirmation = null);
        startConfirmation.show();
    }

    private void begin(FocusTaskStore.Task task) {
        if (!FocusAccessibilityService.isRunning(activity)) {
            Toast.makeText(activity, "请先开启无障碍保护服务", Toast.LENGTH_SHORT).show();
            activity.startActivity(new Intent(activity, PermissionGuideActivity.class));
            return;
        }
        if (sessions.end(System.currentTimeMillis()) > 0) {
            Toast.makeText(activity, "请先完成当前专注或睡眠", Toast.LENGTH_SHORT).show(); return;
        }
        android.os.Bundle result = FocusTaskStore.POMODORO.equals(task.mode)
                ? sessions.startPomodoro(task.value, task.name, task.id) : sessions.startTimed(task);
        if (!result.getBoolean("saved")) {
            Toast.makeText(activity, result.getString("error", "开始失败，请重试"), Toast.LENGTH_SHORT).show(); return;
        }
        refresh();
        activity.moveTaskToBack(true);
    }

    private void manage(FocusTaskStore.Task task) {
        new MaterialAlertDialogBuilder(activity, R.style.HomeDialog).setTitle(task.name)
                .setItems(new String[]{"编辑任务", "删除任务"}, (dialog, which) -> {
                    if (which == 0) edit(task);
                    else new MaterialAlertDialogBuilder(activity, R.style.HomeDialog).setTitle("删除任务？")
                            .setMessage("只删除快捷任务，不会删除已经产生的专注记录。")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("删除", (d, w) -> {
                                if (tasks.delete(task.id)) refresh();
                                else Toast.makeText(activity, "删除失败，请重试", Toast.LENGTH_SHORT).show();
                            }).show();
                }).show();
    }

    private void edit(FocusTaskStore.Task existing) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setHint("这次要专注什么？");
        name.setText(existing == null ? "" : existing.name);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});
        name.setMinHeight(dp(56));
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        form.addView(name, new LinearLayout.LayoutParams(-1, -2));
        RadioGroup modes = new RadioGroup(activity);
        modes.setOrientation(LinearLayout.VERTICAL);
        modes.setPadding(0, dp(12), 0, dp(12));
        String[] names = {"倒计时", "正计时", "番茄钟"};
        String[] keys = {FocusTaskStore.COUNTDOWN, FocusTaskStore.COUNTUP, FocusTaskStore.POMODORO};
        for (int i = 0; i < names.length; i++) {
            RadioButton radio = new RadioButton(activity);
            radio.setId(View.generateViewId());
            radio.setText(names[i]); radio.setTextSize(14);
            radio.setTextColor(activity.getColor(R.color.text_primary));
            radio.setTag(keys[i]);
            radio.setMinHeight(dp(48));
            radio.setButtonTintList(ColorStateList.valueOf(activity.getColor(R.color.accent_text)));
            modes.addView(radio, new RadioGroup.LayoutParams(-1, -2));
            if (existing == null ? i == 0 : keys[i].equals(existing.mode)) radio.setChecked(true);
        }
        form.addView(modes);
        LinearLayout amountRow = new LinearLayout(activity);
        amountRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView amountLabel = label("时长", 15, R.color.text_secondary);
        amountRow.addView(amountLabel, new LinearLayout.LayoutParams(0, -2, 1));
        EditText amount = new EditText(activity);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        amount.setSingleLine(true);
        amount.setSelectAllOnFocus(true);
        amount.setText(String.valueOf(existing == null ? 25 : existing.value));
        amount.setGravity(Gravity.CENTER);
        amount.setTextColor(activity.getColor(R.color.text_primary));
        amount.setBackgroundResource(R.drawable.field_rounded);
        amount.setMinHeight(dp(52));
        amountRow.addView(amount, new LinearLayout.LayoutParams(dp(88), -2));
        TextView unit = label("分钟", 14, R.color.text_secondary);
        unit.setPadding(dp(8), 0, 0, 0);
        amountRow.addView(unit);
        form.addView(amountRow);
        Runnable updateMode = () -> {
            RadioButton selected = form.findViewById(modes.getCheckedRadioButtonId());
            String mode = selected == null ? FocusTaskStore.COUNTDOWN : (String) selected.getTag();
            amountRow.setVisibility(FocusTaskStore.COUNTUP.equals(mode) ? View.GONE : View.VISIBLE);
            amountLabel.setText(FocusTaskStore.POMODORO.equals(mode) ? "轮数" : "时长");
            unit.setText(FocusTaskStore.POMODORO.equals(mode) ? "轮" : "分钟");
            if (FocusTaskStore.POMODORO.equals(mode) && "25".equals(amount.getText().toString())) amount.setText("3");
        };
        modes.setOnCheckedChangeListener((group, checkedId) -> updateMode.run());
        updateMode.run();
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.HomeDialog)
                .setTitle(existing == null ? "创建专注" : "编辑专注")
                .setView(scroll).setNegativeButton("取消", null)
                .setPositiveButton(existing == null ? "保存并开始" : "保存", null);
        if (existing == null) builder.setNeutralButton("仅保存", (d, w) -> {});
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> save(dialog, existing, name, modes, amount, true));
            if (existing == null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(button -> save(dialog, null, name, modes, amount, false));
        });
        dialog.show();
    }

    private void save(AlertDialog dialog, FocusTaskStore.Task existing, EditText name,
                      RadioGroup modes, EditText amount, boolean startNow) {
        String title = name.getText().toString().trim();
        if (title.isEmpty()) { name.setError("给这次专注取个名字"); return; }
        RadioButton selected = dialog.findViewById(modes.getCheckedRadioButtonId());
        String mode = (String) selected.getTag();
        int value = 0;
        if (!FocusTaskStore.COUNTUP.equals(mode)) {
            try { value = Integer.parseInt(amount.getText().toString()); }
            catch (NumberFormatException ignored) { }
            int max = FocusTaskStore.POMODORO.equals(mode) ? 24 : 720;
            if (value < 1 || value > max) { amount.setError("请输入 1 到 " + max); return; }
        }
        FocusTaskStore.Task task;
        if (existing == null) task = tasks.add(title, mode, value);
        else {
            task = new FocusTaskStore.Task(existing.id, title, mode, value);
            if (!tasks.update(task)) task = null;
        }
        if (task == null) { Toast.makeText(activity, "保存失败，请重试", Toast.LENGTH_SHORT).show(); return; }
        dialog.dismiss(); refresh();
        if (startNow && existing == null) start(task);
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(text); view.setTextSize(size);
        view.setTextColor(activity.getColor(color));
        return view;
    }
    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
}
