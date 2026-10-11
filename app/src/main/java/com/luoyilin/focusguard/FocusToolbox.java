package com.luoyilin.focusguard;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/** A sheet inside the existing accessibility overlay, so the app below stays blocked. */
final class FocusToolbox extends FrameLayout {
    private final List<Entry> apps = new ArrayList<>();

    FocusToolbox(Context context, Set<String> allowed, BiConsumer<String, Intent> launch,
                 Runnable wallpaperChanged) {
        super(context);
        PackageManager packages = context.getPackageManager();
        for (String pkg : allowed) {
            Intent intent = packages.getLaunchIntentForPackage(pkg);
            if (intent == null) continue;
            try {
                String name = packages.getApplicationLabel(packages.getApplicationInfo(pkg, 0)).toString();
                apps.add(new Entry(pkg, name, packages.getApplicationIcon(pkg), intent));
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        java.text.Collator collator = java.text.Collator.getInstance();
        apps.sort((a, b) -> collator.compare(a.name, b.name));
        View shade = new View(context);
        shade.setBackgroundColor(0x66000000);
        shade.setContentDescription("收起工具箱");
        shade.setOnClickListener(v -> setVisibility(GONE));
        addView(shade, new LayoutParams(-1, -1));

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(8), dp(16), dp(16));
        panel.setBackgroundResource(R.drawable.surface_rounded);
        panel.setClipToOutline(true);
        panel.setClickable(true);
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText("工具箱"); title.setTextSize(18);
        title.setTextColor(context.getColor(R.color.text_primary));
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton close = new ImageButton(context);
        close.setImageResource(R.drawable.ic_focus_close);
        close.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.text_secondary)));
        close.setBackgroundResource(R.drawable.focus_action_background);
        close.setContentDescription("收起工具箱");
        close.setTooltipText("收起工具箱");
        close.setOnClickListener(v -> setVisibility(GONE));
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(header);
        GridView appsGrid = new GridView(context);
        appsGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        appsGrid.setVerticalSpacing(dp(8));
        appsGrid.setSelector(android.R.color.transparent);
        appsGrid.setAdapter(new BaseAdapter() {
            public int getCount() { return apps.size(); }
            public Entry getItem(int position) { return apps.get(position); }
            public long getItemId(int position) { return position; }
            public View getView(int position, View recycled, ViewGroup parent) {
                Entry app = getItem(position);
                LinearLayout tile = iconAction(context, app.icon, app.name, () -> {
                    setVisibility(GONE); launch.accept(app.pkg, app.intent);
                });
                int height = dp(92 + Math.round(24 * Math.max(0, getResources().getConfiguration().fontScale - 1)));
                tile.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, height));
                return tile;
            }
        });
        GridView wallpapersGrid = new GridView(context);
        wallpapersGrid.setNumColumns(2);
        wallpapersGrid.setVerticalSpacing(dp(8));
        wallpapersGrid.setHorizontalSpacing(dp(8));
        wallpapersGrid.setSelector(android.R.color.transparent);
        android.util.SparseArray<Bitmap> thumbnails = new android.util.SparseArray<>();
        wallpapersGrid.setAdapter(new BaseAdapter() {
            public int getCount() { return AppWallpaper.count(); }
            public Object getItem(int position) { return AppWallpaper.name(position); }
            public long getItemId(int position) { return position; }
            public View getView(int position, View recycled, ViewGroup parent) {
                LinearLayout tile = new LinearLayout(context);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setBackgroundResource(R.drawable.focus_action_background);
                tile.setPadding(dp(4), dp(4), dp(4), dp(4));
                ImageView image = new ImageView(context);
                Bitmap bitmap = thumbnails.get(position);
                if (bitmap == null) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 4;
                    bitmap = BitmapFactory.decodeResource(getResources(), AppWallpaper.resource(position), options);
                    if (bitmap != null) thumbnails.put(position, bitmap);
                }
                image.setImageBitmap(bitmap);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                tile.addView(image, new LinearLayout.LayoutParams(-1, dp(104)));
                TextView caption = new TextView(context);
                caption.setText(AppWallpaper.name(position)
                        + (position == AppWallpaper.selectedIndex(context) ? " · 已选" : ""));
                caption.setTextColor(context.getColor(R.color.text_primary));
                caption.setTextSize(13);
                caption.setGravity(Gravity.CENTER);
                caption.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                tile.addView(caption, new LinearLayout.LayoutParams(-1, dp(28)));
                tile.setContentDescription(AppWallpaper.name(position) + "，预览壁纸");
                tile.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, dp(140)));
                return tile;
            }
        });
        FrameLayout area = new FrameLayout(context);
        area.addView(appsGrid, new LayoutParams(-1, -1));
        area.addView(wallpapersGrid, new LayoutParams(-1, -1));
        wallpapersGrid.setVisibility(GONE);
        TextView empty = new TextView(context);
        empty.setText("暂无允许应用"); empty.setTextSize(15);
        empty.setTextColor(context.getColor(R.color.text_secondary));
        empty.setGravity(Gravity.CENTER);
        area.addView(empty, new LayoutParams(-1, -1)); appsGrid.setEmptyView(empty);
        LinearLayout tabs = new LinearLayout(context);
        Button appsTab = tab(context, "允许应用");
        Button wallpaperTab = tab(context, "壁纸");
        tabs.addView(appsTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        tabs.addView(wallpaperTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        panel.addView(tabs);
        appsTab.setOnClickListener(v -> {
            appsGrid.setVisibility(VISIBLE);
            wallpapersGrid.setVisibility(GONE);
            empty.setVisibility(apps.isEmpty() ? VISIBLE : GONE);
        });
        wallpaperTab.setOnClickListener(v -> {
            appsGrid.setVisibility(GONE);
            empty.setVisibility(GONE);
            wallpapersGrid.setVisibility(VISIBLE);
            ((BaseAdapter) wallpapersGrid.getAdapter()).notifyDataSetChanged();
        });
        panel.addView(area, new LinearLayout.LayoutParams(-1, 0, 1));
        LayoutParams sheet = new LayoutParams(-1, dp(360), Gravity.BOTTOM);
        sheet.setMargins(dp(12), 0, dp(12), dp(12));
        addView(panel, sheet);
        addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int width = r - l, height = b - t;
            if (width <= 0 || height <= 0) return;
            int columns = Math.max(2, Math.min(4, (width - dp(56)) / dp(96)));
            if (appsGrid.getNumColumns() != columns) appsGrid.setNumColumns(columns);
            int target = Math.min(dp(420), Math.round(height * 0.6f));
            if (sheet.height != target) { sheet.height = target; panel.setLayoutParams(sheet); }
        });
        LinearLayout preview = new LinearLayout(context);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(16), dp(16), dp(16), dp(16));
        preview.setBackgroundColor(context.getColor(R.color.surface_background));
        Button back = tab(context, "返回工具箱");
        back.setOnClickListener(v -> preview.setVisibility(GONE));
        preview.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
        ImageView large = new ImageView(context);
        large.setScaleType(ImageView.ScaleType.FIT_CENTER);
        large.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        preview.addView(large, new LinearLayout.LayoutParams(-1, 0, 1));
        Button use = tab(context, "使用这张壁纸");
        preview.addView(use, new LinearLayout.LayoutParams(-1, dp(52)));
        int[] previewIndex = {-1};
        wallpapersGrid.setOnItemClickListener((parent, view, position, id) -> {
            previewIndex[0] = position;
            large.setImageResource(AppWallpaper.resource(position));
            preview.setContentDescription("预览" + AppWallpaper.name(position));
            preview.setVisibility(VISIBLE);
        });
        use.setOnClickListener(v -> {
            if (!AppWallpaper.select(context, previewIndex[0])) {
                Toast.makeText(context, "壁纸保存失败，请重试", Toast.LENGTH_SHORT).show();
                return;
            }
            preview.setVisibility(GONE);
            setVisibility(GONE);
            wallpaperChanged.run();
        });
        addView(preview, new LayoutParams(-1, -1));
        preview.setVisibility(GONE);
        setVisibility(GONE);
    }

    private Button tab(Context context, String title) {
        Button button = new Button(context);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(context.getColor(R.color.text_primary));
        button.setBackgroundResource(R.drawable.focus_action_background);
        return button;
    }

    static LinearLayout iconAction(Context context, Drawable icon, String name, Runnable action) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
        int padding = Math.round(6 * density);
        tile.setPadding(padding, padding, padding, padding);
        tile.setBackgroundResource(R.drawable.focus_action_background);
        tile.setContentDescription(name); tile.setTooltipText(name); tile.setFocusable(true);
        ImageView image = new ImageView(context);
        image.setImageDrawable(icon); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(image, new LinearLayout.LayoutParams(Math.round(40 * density), Math.round(40 * density)));
        TextView label = new TextView(context);
        label.setText(name); label.setTextSize(12); label.setGravity(Gravity.CENTER);
        label.setTextColor(context.getColor(R.color.text_primary));
        label.setMaxLines(2); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams caption = new LinearLayout.LayoutParams(-1, -2);
        caption.topMargin = Math.round(6 * density); tile.addView(label, caption);
        tile.setOnClickListener(v -> action.run());
        return tile;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static final class Entry {
        final String pkg, name;
        final Drawable icon;
        final Intent intent;
        Entry(String pkg, String name, Drawable icon, Intent intent) {
            this.pkg = pkg; this.name = name; this.icon = icon; this.intent = intent;
        }
    }
}
