package com.luoyilin.focusguard;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

final class FocusAppPicker extends Dialog {
    private final Activity activity;
    private final Set<String> selected;
    private final Consumer<Set<String>> save;
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> visible = new ArrayList<>();
    private TextView count;
    private final android.util.LruCache<String, Drawable> icons = new android.util.LruCache<>(64);

    FocusAppPicker(Activity activity, Set<String> selected, Consumer<Set<String>> save) {
        super(activity, Appearance.dark(activity) ? android.R.style.Theme_Material_Dialog_NoActionBar
                : android.R.style.Theme_Material_Light_Dialog_NoActionBar);
        this.activity = activity; this.selected = new HashSet<>(selected); this.save = save;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = new LinearLayout(getContext()); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(8)); body.setBackgroundResource(R.drawable.surface_rounded);
        body.setClipToOutline(true);
        TextView title = text("可用应用", 20); title.setTypeface(null, Typeface.BOLD); body.addView(title);
        EditText search = new EditText(getContext()); search.setSingleLine(true); search.setTextSize(16);
        search.setTextColor(getContext().getColor(R.color.text_primary)); search.setHintTextColor(getContext().getColor(R.color.text_secondary));
        search.setHint("搜索应用"); search.setContentDescription("搜索应用");
        search.setBackgroundResource(R.drawable.field_rounded);
        search.setPadding(dp(12), dp(8), dp(12), dp(8));
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        body.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        count = text("", 14); count.setPadding(0, dp(8), 0, dp(8)); body.addView(count);
        FrameLayout listArea = new FrameLayout(getContext());
        ListView list = new ListView(getContext()); list.setDividerHeight(0);
        TextView empty = text("没有找到匹配的应用", 16); empty.setGravity(Gravity.CENTER);
        listArea.addView(list, new FrameLayout.LayoutParams(-1, -1));
        listArea.addView(empty, new FrameLayout.LayoutParams(-1, -1)); list.setEmptyView(empty);
        body.addView(listArea, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout actions = new LinearLayout(getContext());
        Button cancel = new Button(getContext()); cancel.setText("取消"); cancel.setTextColor(getContext().getColor(R.color.text_primary));
        cancel.setBackgroundResource(R.drawable.button_rounded);
        cancel.setBackgroundTintList(ColorStateList.valueOf(getContext().getColor(R.color.secondary_surface)));
        cancel.setOnClickListener(v -> dismiss()); actions.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1));
        Button confirm = new RaisedButton(getContext()); confirm.setText("保存");
        confirm.setOnClickListener(v -> { save.accept(new HashSet<>(selected)); dismiss(); });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, -2, 1);
        confirmParams.leftMargin = dp(8);
        actions.addView(confirm, confirmParams); body.addView(actions);
        setContentView(body);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : activity.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(activity.getPackageName()) && seen.add(pkg))
                all.add(new Entry(info, info.loadLabel(activity.getPackageManager()).toString(), pkg));
        }
        java.text.Collator collator = java.text.Collator.getInstance(Locale.getDefault());
        all.sort((a, b) -> {
            int chosen = Boolean.compare(selected.contains(b.pkg), selected.contains(a.pkg));
            return chosen != 0 ? chosen : collator.compare(a.name, b.name);
        });
        AppAdapter adapter = new AppAdapter(); list.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int n, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int n) {
                filter(s.toString()); adapter.notifyDataSetChanged(); list.setSelection(0);
            }
            public void afterTextChanged(Editable value) { }
        });
        filter(""); adapter.notifyDataSetChanged();
        body.setFocusableInTouchMode(true); body.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        getWindow().setLayout(Math.min(dp(520), activity.getResources().getDisplayMetrics().widthPixels - dp(24)),
                (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.82));
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            android.graphics.Rect visibleFrame = new android.graphics.Rect();
            getWindow().getDecorView().getWindowVisibleDisplayFrame(visibleFrame);
            int available = visibleFrame.height() - dp(24);
            int height = Math.min((int)(activity.getResources().getDisplayMetrics().heightPixels * 0.82), available);
            if (height > 0 && getWindow().getAttributes().height != height) {
                getWindow().setLayout(getWindow().getAttributes().width, height);
            }
        });
    }
    private void filter(String query) {
        String value = query.trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (Entry e : all) if (e.name.toLowerCase(Locale.ROOT).contains(value)
                || e.pkg.toLowerCase(Locale.ROOT).contains(value)) visible.add(e);
        updateCount();
    }
    private void updateCount() { count.setText("已选择 " + selected.size() + " 个 · 找到 " + visible.size() + " 个应用"); }
    private TextView text(String value, int size) {
        TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size);
        view.setTextColor(getContext().getColor(R.color.text_primary)); return view;
    }
    private int dp(int n) { return Math.round(n * getContext().getResources().getDisplayMetrics().density); }
    private static final class Entry {
        final ResolveInfo info; final String name, pkg;
        Entry(ResolveInfo info, String name, String pkg) { this.info = info; this.name = name; this.pkg = pkg; }
    }
    private final class AppAdapter extends BaseAdapter {
        public int getCount() { return visible.size(); }
        public Entry getItem(int position) { return visible.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View recycled, ViewGroup parent) {
            LinearLayout row;
            if (recycled == null) {
                row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(68)); row.setPadding(0, dp(6), 0, dp(6));
                ImageView icon = new ImageView(getContext()); row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
                LinearLayout labels = new LinearLayout(getContext()); labels.setOrientation(LinearLayout.VERTICAL);
                labels.setPadding(dp(12), 0, dp(4), 0);
                TextView name = text("", 16); name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END);
                TextView pkg = text("", 12); pkg.setTextColor(getContext().getColor(R.color.text_secondary)); pkg.setSingleLine(true);
                pkg.setEllipsize(TextUtils.TruncateAt.MIDDLE); labels.addView(name); labels.addView(pkg);
                row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
                CheckBox checked = new CheckBox(getContext()); checked.setClickable(false); checked.setFocusable(false);
                checked.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                row.addView(checked, new LinearLayout.LayoutParams(dp(48), dp(48)));
            } else row = (LinearLayout) recycled;
            Entry entry = getItem(position);
            Drawable icon = icons.get(entry.pkg);
            if (icon == null) { icon = entry.info.loadIcon(activity.getPackageManager()); icons.put(entry.pkg, icon); }
            ((ImageView)row.getChildAt(0)).setImageDrawable(icon);
            LinearLayout labels = (LinearLayout) row.getChildAt(1);
            ((TextView)labels.getChildAt(0)).setText(entry.name);
            ((TextView)labels.getChildAt(1)).setText(entry.pkg);
            ((CheckBox)row.getChildAt(2)).setChecked(selected.contains(entry.pkg));
            row.setContentDescription(entry.name + (selected.contains(entry.pkg) ? "，已选择" : "，未选择"));
            row.setOnClickListener(v -> {
                if (!selected.remove(entry.pkg)) selected.add(entry.pkg);
                updateCount(); notifyDataSetChanged();
            });
            return row;
        }
    }
}
