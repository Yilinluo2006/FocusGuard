package com.luoyilin.focusguard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.luoyilin.focusguard.sync.LimitSyncManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;


final class AppManagePage {
    private static final ExecutorService READER = Executors.newSingleThreadExecutor();
    private final AppCompatActivity activity;
    private android.view.View root;
    private List<AppInfo> apps;
    private AppListAdapter listAdapter;
    private GridLayoutManager appLayout;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private BooleanSupplier canApplyResults = () -> true;
    private Future<?> readTask;
    private boolean reading, loaded, disposed, filterChosen;
    private final Runnable applyResults = this::applyLoadedResults;
    private List<AppInfo> pendingApps;
    private Map<String, Long> pendingUsage;
    private Integer pendingEmergency;
    private RuntimeException pendingError;

    void setCanApplyResults(BooleanSupplier ready) { canApplyResults = ready; }
    void dispose() {
        disposed = true;
        ui.removeCallbacks(applyResults);
        if (readTask != null) readTask.cancel(true);
        pendingApps = null;
        pendingUsage = null;
    }

    private void updateAppLayout() {
        RecyclerView list = findViewById(R.id.rvAppList);
        boolean compact = !listAdapter.isOnlyLimited();
        float density = activity.getResources().getDisplayMetrics().density;
        float fontScale = activity.getResources().getConfiguration().fontScale;
        float width = (list.getWidth() > 0 ? list.getWidth() : activity.getResources().getDisplayMetrics().widthPixels) / density;
        float minWidth = 150 * Math.max(1f, fontScale / 1.3f);
        int columns = compact ? Math.max(1, Math.min(6, (int) (width / minWidth))) : 1;
        if (appLayout.getSpanCount() != columns) appLayout.setSpanCount(columns);
        listAdapter.setCompactCards(compact);
        list.invalidateItemDecorations();
    }
    private Runnable refreshUsageSummary;
    void attachUsageSummary(android.view.View summary, Runnable refresh) {
        refreshUsageSummary = refresh;
        android.widget.LinearLayout details = new android.widget.LinearLayout(activity);
        details.setOrientation(android.widget.LinearLayout.VERTICAL);
        details.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView selected = findViewById(R.id.tvSelectedCount);
        TextView emergency = findViewById(R.id.emergencySummary);
        ((android.view.ViewGroup) selected.getParent()).removeView(selected);
        ((android.view.ViewGroup) emergency.getParent()).removeView(emergency);
        selected.setTextSize(14);
        emergency.setTextSize(13);
        details.addView(selected, new android.widget.LinearLayout.LayoutParams(-1, -2));
        android.widget.LinearLayout.LayoutParams emergencyParams = new android.widget.LinearLayout.LayoutParams(-1, -2);
        emergencyParams.topMargin = PageUi.dp(activity, 6);
        details.addView(emergency, emergencyParams);
        android.widget.LinearLayout.LayoutParams detailsParams = new android.widget.LinearLayout.LayoutParams(-1, -2);
        detailsParams.topMargin = PageUi.dp(activity, 8);
        ((android.widget.LinearLayout) summary).addView(details, detailsParams);
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Math.round(8 * activity.getResources().getDisplayMetrics().density);
        params.bottomMargin = params.topMargin;
        ((android.widget.LinearLayout) root).addView(summary, 1, params);
    }
    AppManagePage(AppCompatActivity activity) { this.activity = activity; }
    private <T extends android.view.View> T findViewById(int id) { return root.findViewById(id); }
    private boolean isFinishing() { return activity.isFinishing(); }
    private boolean isDestroyed() { return activity.isDestroyed(); }
    private SharedPreferences getSharedPreferences(String name, int mode) { return activity.getSharedPreferences(name, mode); }
    private PackageManager getPackageManager() { return activity.getPackageManager(); }
    private static final int MODE_PRIVATE = Context.MODE_PRIVATE;
    void saveState(Bundle out) {
        out.putString("appSearchQuery", ((android.widget.EditText) findViewById(R.id.appSearch)).getText().toString());
        if (filterChosen) out.putBoolean("appFilterSelected", listAdapter.isOnlyLimited());
    }
    void restoreState(Bundle state) {
        if (state == null) return;
        ((android.widget.EditText) findViewById(R.id.appSearch)).setText(state.getString("appSearchQuery", ""));
        if (!state.containsKey("appFilterSelected")) return;
        filterChosen = true;
        ((com.google.android.material.button.MaterialButtonToggleGroup) findViewById(R.id.appFilterGroup))
                .check(state.getBoolean("appFilterSelected") ? R.id.btnFilterSelected : R.id.btnFilterAll);
    }
    void refresh() {
        if (apps == null || disposed || reading) return;
        reading = true;
        boolean firstLoad = !loaded;
        readTask = READER.submit(() -> {
            List<AppInfo> installed = null;
            Map<String, Long> usage = java.util.Collections.emptyMap();
            Integer emergency = null;
            RuntimeException error = null;
            try {
                if (firstLoad) installed = loadInstalledApps();
                usage = loadTodayUsageMap();
                try { emergency = RestrictionStore.left(activity); }
                catch (java.io.IOException ignored) { }
            } catch (RuntimeException failure) { error = failure; }
            List<AppInfo> resultApps = installed;
            Map<String, Long> resultUsage = usage;
            Integer resultEmergency = emergency;
            RuntimeException resultError = error;
            ui.post(() -> {
                if (disposed || isFinishing() || isDestroyed()) return;
                pendingApps = resultApps;
                pendingUsage = resultUsage;
                pendingEmergency = resultEmergency;
                pendingError = resultError;
                applyLoadedResults();
            });
        });
    }

    private void applyLoadedResults() {
        if (disposed || isFinishing() || isDestroyed()) return;
        if (!canApplyResults.getAsBoolean()) { ui.postDelayed(applyResults, 32); return; }
        reading = false;
        if (pendingError != null) {
            android.util.Log.w("AppManagePage", "Unable to load applications", pendingError);
            pendingError = null;
            updateEmptyState();
            if (!loaded) {
                ((TextView) findViewById(R.id.tvEmptyApps)).setText("应用读取失败");
                Button retry = findViewById(R.id.btnEmptyAction);
                retry.setText("重试"); retry.setVisibility(android.view.View.VISIBLE);
                retry.setOnClickListener(v -> refresh());
            }
            return;
        }
        if (pendingApps != null) { apps.clear(); apps.addAll(pendingApps); pendingApps = null; }
        loaded = true;
        Set<String> selected = getSavedSelectedPackageNames();
        // A user may edit restrictions while a usage read is in flight. Bind current settings, not worker copies.
        for (AppInfo app : apps) {
            app.setSelected(selected.contains(app.getPackageName()));
            app.setLimitMinutes(getSavedLimitMinutes(app.getPackageName()));
            app.setTodayUsageMillis(pendingUsage.getOrDefault(app.getPackageName(), 0L));
        }
        pendingUsage = null;
        if (!filterChosen) {
            ((com.google.android.material.button.MaterialButtonToggleGroup) findViewById(R.id.appFilterGroup))
                    .check(countSelectedApps(apps) > 0 ? R.id.btnFilterSelected : R.id.btnFilterAll);
            filterChosen = true;
        }
        listAdapter.filter(((android.widget.EditText) findViewById(R.id.appSearch)).getText().toString());
        updateSelectedCount();
        updateEmptyState();
        ((TextView) findViewById(R.id.emergencySummary)).setText(DeviceSyncManager.strictQuota(activity) ? "两端共用每日额度"
                : QuotaPresentation.appCount(pendingEmergency == null ? -1 : pendingEmergency));
        if (refreshUsageSummary != null) refreshUsageSummary.run();
    }
    private boolean localSaveFailed;

    private void showSyncStatus(String text) {
        if (isFinishing() || isDestroyed() || localSaveFailed) return;
        TextView status = findViewById(R.id.tvCloudStatus);
        status.setText(text);
        status.setVisibility(android.view.View.GONE);
        status.setTextColor(activity.getColor(text.startsWith("已")
                ? R.color.accent_text : R.color.text_secondary));
        status.setTypeface(null, text.startsWith("已")
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        status.setOnClickListener(null);
        status.setClickable(false);
    }

    private void updateSelectedCount() {
        ((TextView) findViewById(R.id.tvSelectedCount)).setText(
                MetricText.emphasize("已限制 " + countSelectedApps(apps) + " 个", 1.15f));
    }

    private void updateEmptyState() {
        android.view.View empty = findViewById(R.id.emptyApps);
        empty.setVisibility(listAdapter.getItemCount() == 0
                ? android.view.View.VISIBLE : android.view.View.GONE);
        if (listAdapter.getItemCount() != 0) return;
        String query = ((android.widget.EditText) findViewById(R.id.appSearch)).getText().toString().trim();
        TextView message = findViewById(R.id.tvEmptyApps);
        Button action = findViewById(R.id.btnEmptyAction);
        if (!loaded && reading) {
            message.setText("加载应用…");
            action.setVisibility(android.view.View.GONE);
        } else if (!query.isEmpty()) {
            message.setText("没有匹配的应用");
            action.setText("清除搜索");
            action.setVisibility(android.view.View.VISIBLE);
            action.setOnClickListener(v -> ((android.widget.EditText) findViewById(R.id.appSearch)).setText(""));
        } else if (listAdapter.isOnlyLimited()) {
            message.setText("暂无受限应用");
            action.setText("查看全部应用");
            action.setVisibility(android.view.View.VISIBLE);
            action.setOnClickListener(v -> ((com.google.android.material.button.MaterialButtonToggleGroup)
                    findViewById(R.id.appFilterGroup)).check(R.id.btnFilterAll));
        } else {
            message.setText("没有可显示的应用");
            action.setVisibility(android.view.View.GONE);
        }
    }

    private static final String PREF_NAME = "focus_guard_prefs";
    // SharedPreferences 文件名

    private static final String KEY_SELECTED_PACKAGES = "selected_packages";
    // 保存已选择应用包名集合的 key

    private static final String KEY_LIMIT_PREFIX = "limit_minutes_";
    // 保存应用限制时间时使用的 key 前缀

    private static final int DEFAULT_LIMIT_MINUTES = 30;
    // 默认限制时间为 30 分钟

    android.view.View create() {
        root = activity.getLayoutInflater().inflate(R.layout.activity_app_manage, null, false);
        PageUi.secondary(findViewById(R.id.btnRestrictionRules));
        findViewById(R.id.btnRestrictionRules).setOnClickListener(v -> RestrictionSettings.show(activity));
        // 加载应用管理页面

        RecyclerView rvAppList = findViewById(R.id.rvAppList);
        // 获取页面控件

        List<AppInfo> appList = new ArrayList<>();
        apps = appList;
        // 读取手机中已安装的应用

        updateSelectedCount();
        // 显示当前已选择应用数量

        AppListAdapter adapter = new AppListAdapter(appList);
        listAdapter = adapter;
        ((android.widget.EditText) findViewById(R.id.appSearch)).addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
                updateEmptyState();
            }
            public void afterTextChanged(android.text.Editable s) { }
        });
        // 创建列表适配器

        adapter.setOnSelectionChangedListener(selectedCount -> {
            updateSelectedCount();
            LimitSyncManager.markPending(activity);
            if (saveSelectedApps(appList)) showSyncStatus("已保存在本机");
            updateEmptyState();
            // 选择状态改变后保存到手机本地
        });

        appLayout = new GridLayoutManager(activity, 1);
        rvAppList.setLayoutManager(appLayout);
        rvAppList.setAdapter(adapter);
        rvAppList.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override public void getItemOffsets(android.graphics.Rect out, android.view.View view,
                    RecyclerView parent, RecyclerView.State state) {
                out.setEmpty();
                int position = parent.getChildAdapterPosition(view);
                int columns = appLayout.getSpanCount();
                if (columns <= 1 || position == RecyclerView.NO_POSITION) return;
                int gap = PageUi.dp(activity, 8);
                int column = position % columns;
                out.left = column * gap / columns;
                out.right = gap - (column + 1) * gap / columns;
            }
        });
        rvAppList.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (r - l != oldR - oldL) updateAppLayout();
        });
        // 设置 RecyclerView 为竖向列表并绑定适配器

        com.google.android.material.button.MaterialButtonToggleGroup group = findViewById(R.id.appFilterGroup);
        group.addOnButtonCheckedListener((buttons, checkedId, isChecked) -> {
            if (!isChecked) return;
            filterChosen = true;
            adapter.showOnlyLimited(checkedId == R.id.btnFilterSelected);
            updateAppLayout();
            rvAppList.scrollToPosition(0);
            updateEmptyState();
        });
        group.check(R.id.btnFilterAll);
        filterChosen = false;
        showSyncStatus("本机自动保存");
        refresh();
        updateEmptyState();
        return root;
    }

    private List<AppInfo> loadInstalledApps() {
        List<AppInfo> appList = new ArrayList<>();
        // 保存最终展示的应用

        Set<String> selectedPackageNames =
                getSavedSelectedPackageNames();
        // 获取本地保存的已选择包名


        PackageManager packageManager =
                getPackageManager();

        Intent intent = new Intent(
                Intent.ACTION_MAIN,
                null
        );

        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        // 只查询能够从桌面启动的应用

        List<ResolveInfo> resolveInfoList =
                packageManager.queryIntentActivities(
                        intent,
                        0
                );

        Set<String> addedPackages = new HashSet<>();
        // 防止同一个应用因为多个启动入口而重复显示

        for (ResolveInfo resolveInfo : resolveInfoList) {
            if (Thread.currentThread().isInterrupted()) return appList;
            ApplicationInfo applicationInfo =
                    resolveInfo.activityInfo.applicationInfo;

            if ((applicationInfo.flags
                    & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }
            // 跳过系统应用

            String packageName =
                    applicationInfo.packageName;

            if (!addedPackages.add(packageName)) {
                continue;
            }
            // 已加入列表的包名不再重复添加

            String appName = packageManager
                    .getApplicationLabel(applicationInfo)
                    .toString();

            Drawable appIcon = packageManager
                    .getApplicationIcon(applicationInfo);

            AppInfo appInfo = new AppInfo(
                    appName,
                    packageName,
                    appIcon
            );

            boolean isSelected =
                    selectedPackageNames.contains(packageName);

            appInfo.setSelected(isSelected);
            // 恢复本地选择状态

            if (isSelected) {
                appInfo.setLimitMinutes(
                        getSavedLimitMinutes(packageName)
                );
            }
            // 恢复本地限制时间


            appList.add(appInfo);
        }

        return appList;
    }

    private Map<String, Long> loadTodayUsageMap() {
        Map<String, Long> usage = AppUsageReader.totals(activity, System.currentTimeMillis(), null);
        Map<String, Long> local = usage == null ? java.util.Collections.emptyMap() : usage;
        return DeviceSyncManager.strictQuota(activity) ? DeviceSyncManager.combinedToday(activity, local) : local;
    }
    private Set<String> getSavedSelectedPackageNames() {
        SharedPreferences preferences =
                getSharedPreferences(
                        PREF_NAME,
                        MODE_PRIVATE
                );

        Set<String> savedSet = preferences.getStringSet(
                KEY_SELECTED_PACKAGES,
                new HashSet<>()
        );

        return new HashSet<>(savedSet);
        // 返回副本，避免直接修改 SharedPreferences 内部集合
    }

    private int getSavedLimitMinutes(String packageName) {
        SharedPreferences preferences =
                getSharedPreferences(
                        PREF_NAME,
                        MODE_PRIVATE
                );

        return preferences.getInt(
                KEY_LIMIT_PREFIX + packageName,
                DEFAULT_LIMIT_MINUTES
        );
    }

    private boolean saveSelectedApps(List<AppInfo> appList) {
        Set<String> selectedPackageNames =
                new HashSet<>();

        SharedPreferences preferences =
                getSharedPreferences(
                        PREF_NAME,
                        MODE_PRIVATE
                );

        SharedPreferences.Editor editor =
                preferences.edit();

        Set<String> previous = new HashSet<>(preferences.getStringSet(
                KEY_SELECTED_PACKAGES, java.util.Collections.emptySet()));
        if (!RestrictionStore.canRelax(activity)) {
            for (AppInfo app : appList) {
                int old = preferences.getInt(KEY_LIMIT_PREFIX + app.getPackageName(), DEFAULT_LIMIT_MINUTES);
                if (RestrictionRules.isRelaxation(previous.contains(app.getPackageName()), old,
                        app.isSelected(), app.getLimitMinutes())) {
                    for (AppInfo item : appList) {
                        item.setSelected(previous.contains(item.getPackageName()));
                        item.setLimitMinutes(preferences.getInt(
                                KEY_LIMIT_PREFIX + item.getPackageName(), DEFAULT_LIMIT_MINUTES));
                    }
                    listAdapter.filter(((android.widget.EditText) findViewById(R.id.appSearch))
                            .getText().toString());
                    updateSelectedCount();
                    updateEmptyState();
                    Toast.makeText(activity, "现在不能增加时长或关闭限制，原设置已保留", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        }
        // Preserve restrictions for temporarily unavailable launcher apps.
        selectedPackageNames.addAll(previous);
        for (AppInfo appInfo : appList) {
            String packageName =
                    appInfo.getPackageName();

            if (appInfo.isSelected()) {
                selectedPackageNames.add(packageName);

                editor.putInt(
                        KEY_LIMIT_PREFIX + packageName,
                        appInfo.getLimitMinutes()
                );
            } else {
                selectedPackageNames.remove(packageName);
                editor.remove(
                        KEY_LIMIT_PREFIX + packageName
                );
            }
        }

        editor.putStringSet(
                KEY_SELECTED_PACKAGES,
                selectedPackageNames
        );

        boolean relaxed = false;
        for (AppInfo app : appList) {
            relaxed |= RestrictionRules.isRelaxation(previous.contains(app.getPackageName()),
                    preferences.getInt(KEY_LIMIT_PREFIX + app.getPackageName(), DEFAULT_LIMIT_MINUTES),
                    app.isSelected(), app.getLimitMinutes());
        }
        boolean persisted = editor.commit();
        if (persisted && refreshUsageSummary != null) refreshUsageSummary.run();
        if (persisted && relaxed) StreakStore.breakToday(activity, "取消应用限制或增加时长");
        boolean published = persisted && LimitSnapshot.publish(activity);
        localSaveFailed = !published;
        if (localSaveFailed && !isFinishing() && !isDestroyed()) {
            String error = persisted ? "配置已保存，但未能更新保护服务，请重试"
                    : "本机保存失败，请重试";
            TextView status = findViewById(R.id.tvCloudStatus);
            status.setVisibility(android.view.View.VISIBLE);
            status.setText(persisted ? "限制未生效 · 点击重试" : "保存失败 · 点击重试");
            status.setTextColor(activity.getColor(R.color.danger_text));
            status.setTypeface(null, android.graphics.Typeface.BOLD);
            status.setOnClickListener(v -> {
                if (saveSelectedApps(apps)) showSyncStatus("已保存在本机");
            });
            Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
        }
        return published;
    }

    private int countSelectedApps(List<AppInfo> appList) {
        int count = 0;

        for (AppInfo appInfo : appList) {
            if (appInfo.isSelected()) {
                count++;
            }
        }

        return count;
    }

}
