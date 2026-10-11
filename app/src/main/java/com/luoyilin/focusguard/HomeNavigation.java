package com.luoyilin.focusguard;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigationrail.NavigationRailView;

final class HomeNavigation {
    private final AppCompatActivity activity;
    private final ViewPager pager;
    private final TextView warning;
    private final AppManagePage apps;
    private final FocusModePage focus;
    private final HomeFocus homeFocus;
    private final StatisticsPage statistics;
    private final View[] pages;
    private final TextView localStatus;
    private final TextView setupEntryStatus;
    private final ProtectionStatusRow[] protectionRows = new ProtectionStatusRow[3];
    private final FrameLayout appsContainer;
    private final View usageSummary;
    private final Runnable refreshToday;
    private Bundle appState;
    private boolean appsCreated;
    private int scrollState = ViewPager.SCROLL_STATE_IDLE;
    private boolean disposed;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshVisible = this::refreshVisiblePage;

    HomeNavigation(AppCompatActivity activity, Bundle state, Runnable refreshToday) {
        this.activity = activity;
        this.refreshToday = refreshToday;
        appState = state == null ? null : new Bundle(state);
        View today = activity.findViewById(R.id.main);
        ((ViewGroup) today.getParent()).removeView(today);
        today.setId(View.NO_ID);
        LinearLayout settings = column();
        TextView heading = PageUi.heading(activity, "设置", 28);
        settings.addView(heading);
        LinearLayout accountCard = PageUi.card(activity);
        LinearLayout generalCard = PageUi.card(activity);
        LinearLayout hardeningCard = PageUi.card(activity);
        hardeningCard.setBackgroundResource(R.drawable.settings_hardening_background);
        TextView hardeningHeading = PageUi.heading(activity, "必要加固", 18);
        hardeningHeading.setTextColor(activity.getColor(R.color.accent_text));
        hardeningCard.addView(hardeningHeading);
        TextView hardeningDescription = text("让保护更稳定，建议优先完成以下两项", 12);
        hardeningDescription.setTextColor(activity.getColor(R.color.text_secondary));
        hardeningDescription.setPadding(0, dp(8), 0, 0);
        hardeningCard.addView(hardeningDescription);
        LinearLayout setupCard = PageUi.card(activity);
        LinearLayout setupHeading = new LinearLayout(activity);
        setupHeading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        setupHeading.addView(PageUi.heading(activity, "保护状态", 18), new LinearLayout.LayoutParams(0, -2, 1));
        TextView setupStatus = text("检查中", 13);
        setupEntryStatus = setupStatus;
        setupStatus.setId(R.id.setupEntryStatus);
        setupStatus.setTextColor(activity.getColor(R.color.accent_text));
        setupHeading.addView(setupStatus);
        setupCard.addView(setupHeading);
        TextView setupDescription = text("开关亮起表示保护可用，点击进入管理", 12);
        setupDescription.setTextColor(activity.getColor(R.color.text_secondary));
        setupDescription.setPadding(0, dp(8), 0, dp(8));
        setupCard.addView(setupDescription);
        for (int i = 0; i < protectionRows.length; i++) {
            protectionRows[i] = new ProtectionStatusRow(activity);
            setupCard.addView(protectionRows[i]);
        }
        localStatus = today.findViewById(R.id.tvCloudStatus);
        int[] settingsIds = {R.id.tvCurrentAccount, R.id.tvCloudStatus, R.id.btnUsagePermission,
                R.id.btnLogout, R.id.btnAppearance};
        for (int id : settingsIds) {
            View child = today.findViewById(id);
            ((ViewGroup) child.getParent()).removeView(child);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(8);
            if (child instanceof Button) settingsRow((Button) child);
            if (id == R.id.tvCurrentAccount) {
                ((TextView) child).setTextSize(20);
                ((TextView) child).setTypeface(null, android.graphics.Typeface.BOLD);
            }
            if (id == R.id.tvCloudStatus) {
                ((TextView) child).setTextSize(12);
                ((TextView) child).setTypeface(null, android.graphics.Typeface.NORMAL);
                ((TextView) child).setTextColor(activity.getColor(R.color.text_secondary));
            }
            if (id == R.id.btnUsagePermission) {
                hardeningEntry(hardeningCard, (Button) child, "开启使用记录、无障碍和后台运行权限");
            } else {
                (id == R.id.btnAppearance ? generalCard : accountCard).addView(child, params);
            }
        }
        hardeningEntry(hardeningCard,
                command("壁纸守护 · 备用额度保护", () -> activity.startActivity(new Intent(activity, WallpaperGuardActivity.class))),
                "无障碍断开时，为应用额度提供备用保护");
        generalCard.addView(command("桌面小组件", () -> WidgetPin.show(activity)));
        Button deviceSync = command("手机与平板同步", () -> activity.startActivity(new Intent(activity, DeviceSyncActivity.class)));
        deviceSync.setId(R.id.deviceSyncEntry);
        accountCard.addView(deviceSync);
        PageUi.addCard(settings, hardeningCard);
        PageUi.addCard(settings, setupCard);
        settingsSection(settings, "可选功能");
        LinearLayout optionalCard = PageUi.card(activity);
        Button pageMonitoring = command("页面监控与退出提醒", () -> activity.startActivity(new Intent(activity, PageMonitoringActivity.class)));
        pageMonitoring.setId(R.id.pageMonitoringEntry); optionalCard.addView(pageMonitoring);
        TextView optionalNote = text("独立授权，默认关闭；可随时撤回，不影响原有保护设置", 12);
        optionalNote.setTextColor(activity.getColor(R.color.text_secondary));
        optionalNote.setPadding(0, dp(8), 0, 0); optionalCard.addView(optionalNote);
        PageUi.addCard(settings, optionalCard);
        settingsSection(settings, "账号与数据");
        PageUi.addCard(settings, accountCard);
        settingsSection(settings, "个性化");
        PageUi.addCard(settings, generalCard);
        TextView version = text(activity.getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME, 12);
        version.setTextColor(activity.getColor(R.color.text_secondary));
        version.setGravity(android.view.Gravity.CENTER);
        version.setPadding(0, dp(20), 0, dp(12));
        settings.addView(version);
        ScrollView settingsScroll = new ScrollView(activity);
        settingsScroll.setBackgroundColor(activity.getColor(R.color.page_background));
        settingsScroll.setVerticalScrollBarEnabled(false);
        settingsScroll.addView(settings);
        today.findViewById(R.id.btnManageApps).setVisibility(View.GONE);
        today.findViewById(R.id.btnFocusMode).setVisibility(View.GONE);
        LinearLayout todayBody = (LinearLayout) ((ScrollView) today).getChildAt(0);
        int gutter = activity.getResources().getDimensionPixelSize(R.dimen.home_page_gutter);
        todayBody.setPadding(gutter, dp(20), gutter, dp(24));
        homeFocus = new HomeFocus(activity);
        LinearLayout.LayoutParams homeParams = new LinearLayout.LayoutParams(-1, -2);
        todayBody.addView(homeFocus.view(), 0, homeParams);
        apps = new AppManagePage(activity);
        appsContainer = new FrameLayout(activity);
        focus = new FocusModePage(activity);
        statistics = new StatisticsPage(activity, state);
        pages = new View[]{today, appsContainer, focus.create(), statistics.view(), settingsScroll};
        if (state != null) {
            for (int i = 0; i < pages.length; i++) {
                if (!(pages[i] instanceof ScrollView)) continue;
                ScrollView scroll = (ScrollView) pages[i];
                int scrollY = state.getInt("homePageScroll" + i, 0);
                scroll.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() {
                        scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                        scroll.scrollTo(0, scrollY);
                        return true;
                    }
                });
            }
        }
        usageSummary = today.findViewById(R.id.usageSummaryCard);
        ((ViewGroup) usageSummary.getParent()).removeView(usageSummary);
        for (View page : pages) page.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        ((ScrollView) pages[2]).getChildAt(0).setBackgroundColor(android.graphics.Color.TRANSPARENT);
        focus.restoreState(state);

        LinearLayout shell = new LinearLayout(activity);
        shell.setId(R.id.main);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        warning = text("应用限制已暂停 · 点击恢复", 14);
        warning.setTextColor(activity.getColor(R.color.danger_text));
        warning.setMinHeight(dp(48));
        warning.setGravity(android.view.Gravity.CENTER);
        warning.setPadding(dp(20), dp(12), dp(20), dp(12));
        warning.setBackgroundResource(R.drawable.home_status_background);
        warning.setOnClickListener(v -> activity.startActivity(new Intent(activity, PermissionGuideActivity.class)));
        warning.setVisibility(View.GONE);
        shell.addView(warning, new LinearLayout.LayoutParams(-1, -2));
        pager = new ViewPager(activity);
        pager.setId(R.id.homePager);
        pager.setOffscreenPageLimit(3);
        pager.setAdapter(new PagerAdapter() {
            @Override public int getCount() { return pages.length; }
            @Override public boolean isViewFromObject(View v, Object o) { return v == o; }
            @Override public Object instantiateItem(ViewGroup container, int position) {
                int maxWidth = position == 2 || position == 4 ? 720 : position == 3 ? 960 : 1080;
                AdaptivePageFrame frame = new AdaptivePageFrame(activity, pages[position], maxWidth);
                container.addView(frame);
                return frame;
            }
            @Override public void destroyItem(ViewGroup container, int position, Object object) {
                AdaptivePageFrame frame = (AdaptivePageFrame) object;
                frame.removeView(pages[position]);
                container.removeView(frame);
            }
        });
        boolean wide = activity.getResources().getConfiguration().screenWidthDp >= 600;
        NavigationBarView nav = wide ? new NavigationRailView(activity) : new BottomNavigationView(activity);
        nav.setId(R.id.homeNavigation);
        nav.setBackgroundColor(activity.getColor(R.color.surface_background));
        nav.setLabelVisibilityMode(1);
        nav.setMinimumHeight(0);
        if (nav instanceof BottomNavigationView)
            ((BottomNavigationView) nav).setItemHorizontalTranslationEnabled(false);
        else {
            ((NavigationRailView) nav).setMenuGravity(android.view.Gravity.CENTER);
            ((NavigationRailView) nav).setItemMinimumHeight(dp(76));
        }
        nav.setItemIconSize(dp(24));
        nav.setItemPaddingTop(dp(10));
        nav.setItemPaddingBottom(dp(10));
        nav.setItemTextAppearanceActive(R.style.HomeNavigationLabel);
        nav.setItemTextAppearanceInactive(R.style.HomeNavigationLabel);
        ColorStateList colors = new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{activity.getColor(R.color.accent_text), activity.getColor(R.color.text_secondary)});
        nav.setItemTextColor(colors);
        nav.setItemIconTintList(colors);
        nav.setItemActiveIndicatorColor(ColorStateList.valueOf(activity.getColor(R.color.secondary_surface)));
        String[] titles = {"今日", "应用", "计划", "统计", "设置"};
        int[] icons = {R.drawable.ic_nav_today, R.drawable.ic_nav_apps,
                R.drawable.ic_nav_plan, R.drawable.ic_nav_statistics, R.drawable.ic_nav_settings};
        for (int i = 0; i < titles.length; i++) nav.getMenu().add(0, i + 1, i, titles[i]).setIcon(icons[i]);
        nav.setOnItemSelectedListener(item -> { pager.setCurrentItem(item.getItemId() - 1, true); return true; });
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override public void onPageSelected(int position) {
                nav.getMenu().findItem(position + 1).setChecked(true);
                View focused = activity.getCurrentFocus();
                if (focused != null) {
                    ((android.view.inputmethod.InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                            .hideSoftInputFromWindow(focused.getWindowToken(), 0);
                    focused.clearFocus();
                }
                refresh();
            }
            @Override public void onPageScrollStateChanged(int state) {
                scrollState = state;
                if (state == ViewPager.SCROLL_STATE_IDLE) refresh();
                else ui.removeCallbacks(refreshVisible);
            }
        });
        int navHeight = activity.getResources().getDimensionPixelSize(R.dimen.home_navigation_height);
        if (wide) {
            LinearLayout workspace = new LinearLayout(activity);
            workspace.setOrientation(LinearLayout.HORIZONTAL);
            workspace.addView(nav, new LinearLayout.LayoutParams(dp(88), -1));
            workspace.addView(pager, new LinearLayout.LayoutParams(0, -1, 1));
            shell.addView(workspace, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            shell.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1));
            shell.addView(nav, new LinearLayout.LayoutParams(-1, navHeight));
        }
        activity.setContentView(AppWallpaper.wrapMain(activity, shell, 80));
        // The shell owns system insets; do not apply navigation-bar padding a second time.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(nav, (v, insets) -> insets);
        int restoredTab = state == null ? 0 : state.getInt("homeTab", 0);
        if (state != null && !state.getBoolean("hasStatisticsTab", false) && restoredTab == 3) restoredTab = 4;
        pager.setCurrentItem(Math.max(0, Math.min(pages.length - 1, restoredTab)), false);
        nav.getMenu().findItem(pager.getCurrentItem() + 1).setChecked(true);
        activity.getOnBackPressedDispatcher().addCallback(activity, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (pager.getCurrentItem() != 0) pager.setCurrentItem(0, true);
                else activity.moveTaskToBack(true);
            }
        });
    }
    private void ensureApps() {
        if (appsCreated) return;
        appsCreated = true;
        apps.setCanApplyResults(() -> !disposed && scrollState == ViewPager.SCROLL_STATE_IDLE);
        appsContainer.addView(apps.create(), new FrameLayout.LayoutParams(-1, -1));
        apps.attachUsageSummary(usageSummary, refreshToday);
        apps.restoreState(appState);
        appState = null;
    }
    void save(Bundle out) {
        out.putInt("homeTab", pager.getCurrentItem());
        out.putBoolean("hasStatisticsTab", true);
        for (int i = 0; i < pages.length; i++) {
            if (pages[i] instanceof ScrollView) out.putInt("homePageScroll" + i, pages[i].getScrollY());
        }
        statistics.save(out);
        if (appsCreated) apps.saveState(out);
        else if (appState != null) {
            out.putString("appSearchQuery", appState.getString("appSearchQuery", ""));
            if (appState.containsKey("appFilterSelected"))
                out.putBoolean("appFilterSelected", appState.getBoolean("appFilterSelected"));
        }
        focus.saveState(out);
    }
    void refresh() {
        ui.removeCallbacks(refreshVisible);
        if (!disposed && scrollState == ViewPager.SCROLL_STATE_IDLE) ui.post(refreshVisible);
    }
    private void refreshVisiblePage() {
        if (disposed || scrollState != ViewPager.SCROLL_STATE_IDLE) return;
        refreshStatus();
        if (pager.getCurrentItem() == 0) homeFocus.refresh();

        if (pager.getCurrentItem() == 1) { ensureApps(); apps.refresh(); }
        if (pager.getCurrentItem() == 2) focus.refreshConfiguration();
        if (pager.getCurrentItem() == 3) statistics.refresh();
    }
    void refreshStatus() {
        if (disposed || scrollState != ViewPager.SCROLL_STATE_IDLE) return;
        if (pager.getCurrentItem() == 0) homeFocus.refreshActive();
        boolean enabled = FocusAccessibilityService.isEnabled(activity);
        warning.setText(ProtectionWarning.dailyBackupRunning(activity) ? "睡眠与专注保护待恢复 · 点击检查"
                : enabled ? "应用保护已断开 · 点击重新连接" : "必要设置未完成 · 点击开启");
        warning.setVisibility(ProtectionWarning.healthy(activity) ? View.GONE : View.VISIBLE);
        if (pager.getCurrentItem() == 2) focus.refresh();
        if (pager.getCurrentItem() == 4) {
            localStatus.setText("云端备份："+CloudBackupManager.status(activity)+"\n设备同步："+DeviceSyncManager.status(activity)
                    +"\n"+DeviceSyncManager.lastSuccess(activity));
            int completed = new SetupStatus(activity).completed();
            ProtectionDashboardSnapshot snapshot = ProtectionDashboardSnapshot.read(activity);
            int availableCount = (snapshot.daily.good ? 1 : 0) + (snapshot.sleep.good ? 1 : 0) + (snapshot.focus.good ? 1 : 0);
            setupEntryStatus.setText(availableCount > 0 ? availableCount + " 项可用" : "需要修复");
            ProtectionDashboardSnapshot.Row[] rows = {snapshot.daily, snapshot.sleep, snapshot.focus};
            for (int i = 0; i < rows.length; i++) {
                ProtectionDashboardSnapshot.Row row = rows[i];
                protectionRows[i].bind(row, () -> activity.startActivity(new Intent(activity,
                        row.destination == ProtectionDashboardSnapshot.WALLPAPER ? WallpaperGuardActivity.class
                                : row.destination == ProtectionDashboardSnapshot.APPS ? AppManageActivity.class
                                : row.destination == ProtectionDashboardSnapshot.PLAN ? FocusModeActivity.class
                                : row.good ? ProtectionDashboardActivity.class : PermissionGuideActivity.class)));
            }
            ((Button) pages[4].findViewById(R.id.btnUsagePermission)).setText(
                    completed == 4 ? "查看必要设置  ›" : "去开启必要设置  ›");
        }
    }
    void dispose() {
        disposed = true;
        ui.removeCallbacks(refreshVisible);
        apps.dispose();
    }
    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(20), dp(20), dp(20), dp(20));
        return view;
    }
    private TextView text(String value, int size) {
        TextView view = new TextView(activity);
        view.setText(value); view.setTextSize(size);
        view.setTextColor(activity.getColor(R.color.text_primary));
        return view;
    }
    private void settingsSection(LinearLayout parent, String label) {
        TextView title = text(label, 12);
        title.setTextColor(activity.getColor(R.color.text_secondary));
        title.setPadding(dp(4), dp(20), 0, 0);
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        parent.addView(title);
    }
    private void hardeningEntry(LinearLayout card, Button button, String description) {
        button.setTextColor(activity.getColor(R.color.accent_text));
        button.setTextSize(16);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        card.addView(button, params);
        TextView caption = text(description, 12);
        caption.setTextColor(activity.getColor(R.color.text_secondary));
        caption.setPadding(dp(14), dp(6), dp(14), 0);
        card.addView(caption);
    }
    private void settingsRow(Button button) {
        if (button instanceof RaisedButton) ((RaisedButton)button).setRaised(false);
        button.setBackgroundTintList(null);
        button.setBackgroundResource(R.drawable.page_action_background);
        button.setStateListAnimator(null);
        button.setTextColor(activity.getColor(R.color.text_primary));
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(48));
        button.setPadding(dp(14), dp(12), dp(14), dp(12));
    }
    private Button command(String label, Runnable action) {
        Button button = new Button(activity);
        button.setText(label + "   ›"); settingsRow(button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        button.setLayoutParams(params);
        button.setOnClickListener(v -> action.run());
        return button;
    }
}
