package com.luoyilin.focusguard;

import android.app.Dialog;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

/** In-app decoration only. Never changes the system wallpaper or guard service. */
final class AppWallpaper {
    private static final String PREFS = "app_wallpaper";
    private static final String[] KEYS = {"clouds", "ocean", "mirror", "window", "city", "bamboo", "nap"};
    private static final String[] NAMES = {"云端听歌", "海上漂浮", "天空之镜", "窗边发呆", "雾中城市", "竹影小憩", "云上好梦"};
    private static final int[] IMAGES = {R.drawable.wallpaper_clouds, R.drawable.wallpaper_ocean,
            R.drawable.wallpaper_mirror, R.drawable.wallpaper_window, R.drawable.wallpaper_city,
            R.drawable.wallpaper_bamboo, R.drawable.wallpaper_nap};
    private final AppCompatActivity activity;

    AppWallpaper(AppCompatActivity activity) {
        this.activity = activity;
    }
    static View wrapMain(Context context, View content, int footerDp) {
        if (Appearance.dark(context)) {
            FrameLayout frame = new FrameLayout(context);
            frame.setBackgroundColor(context.getColor(R.color.page_background));
            frame.addView(content, new FrameLayout.LayoutParams(-1, -1));
            return frame;
        }
        ImageView image = new ImageView(context);
        // Keep the ornamental edges below the system icons, with texture behind them.
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        image.setImageResource(R.drawable.app_black_gold);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout backdrop = new FrameLayout(context);
        ImageView statusTexture = new ImageView(context);
        statusTexture.setImageResource(R.drawable.app_black_gold);
        // A shallow center crop samples the undecorated middle of the same texture.
        statusTexture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        statusTexture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        backdrop.addView(image, new FrameLayout.LayoutParams(-1, -1));
        backdrop.addView(statusTexture, new FrameLayout.LayoutParams(-1, 0));
        View frame = wrap(context, content, backdrop, Appearance.dark(context) ? 0x00000000 : 0xE6FFFFFF);
        frame.setBackgroundColor(context.getColor(R.color.page_background));
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(backdrop, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) image.getLayoutParams();
            // Lift the fine top border into the gap above page headings.
            params.topMargin = Math.max(0, bars.top - Math.round(12 * context.getResources().getDisplayMetrics().density));
            params.bottomMargin = bars.bottom + Math.round(footerDp * context.getResources().getDisplayMetrics().density);
            params.leftMargin = bars.left;
            params.rightMargin = bars.right;
            image.setLayoutParams(params);
            FrameLayout.LayoutParams textureParams = (FrameLayout.LayoutParams) statusTexture.getLayoutParams();
            textureParams.height = bars.top;
            statusTexture.setLayoutParams(textureParams);
            return insets;
        });
        return frame;
    }
    static int selectedResource(Context context) {
        return IMAGES[selectedIndex(context)];
    }
    static int count() { return IMAGES.length; }
    static String name(int index) { return NAMES[index]; }
    static int resource(int index) { return IMAGES[index]; }
    static boolean validKey(String key) {
        for (String known : KEYS) if (known.equals(key)) return true;
        return false;
    }
    static int selectedIndex(Context context) {
        Object value = FocusPreferences.read(context, PREFS).get("selected");
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(value)) return i;
        return 0;
    }
    static boolean select(Context context, int index) {
        if (index < 0 || index >= KEYS.length) return false;
        Bundle extras = new Bundle();
        extras.putString("selected", KEYS[index]);
        try {
            Bundle result = context.getContentResolver().call(LimitConfigProvider.URI,
                    "selectWallpaper", PREFS, extras);
            return result != null && result.getBoolean("saved");
        } catch (RuntimeException error) {
            android.util.Log.e("AppWallpaper", "Cannot save selected wallpaper", error);
            return false;
        }
    }
    static View wrap(Context context, View content, int resource) {
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageResource(resource);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return wrap(context, content, image,
                Appearance.dark(context) ? 0x70101010 : 0xC7F6F8FA);
    }
    private static FrameLayout wrap(Context context, View content, View background, int veilColor) {
        FrameLayout frame = new FrameLayout(context);
        frame.addView(background, new FrameLayout.LayoutParams(-1, -1));
        View veil = new View(context);
        veil.setBackgroundColor(veilColor);
        veil.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(veil, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(content, new FrameLayout.LayoutParams(-1, -1));
        return frame;
    }
    private int selected() {
        return selectedIndex(activity);
    }
    void choose() {
        Dialog dialog = new Dialog(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundResource(R.drawable.surface_rounded);
        body.setClipToOutline(true);
        body.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView title = label("专注与睡眠壁纸", 22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        body.addView(title);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        int active = selected();
        for (int row = 0; row < (IMAGES.length + 1) / 2; row++) {
            LinearLayout pair = new LinearLayout(activity);
            grid.addView(pair, new LinearLayout.LayoutParams(-1, -2));
            for (int col = 0; col < 2; col++) {
                final int index = row * 2 + col;
                LinearLayout cell = new LinearLayout(activity);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setPadding(dp(5), dp(8), dp(5), dp(8));
                pair.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
                if (index >= IMAGES.length) continue;
                ImageView thumbnail = new ImageView(activity);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 4;
                thumbnail.setImageBitmap(BitmapFactory.decodeResource(activity.getResources(), IMAGES[index], options));
                thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumbnail.setBackgroundResource(R.drawable.surface_rounded);
                thumbnail.setClipToOutline(true);
                cell.addView(thumbnail, new LinearLayout.LayoutParams(-1, dp(190)));
                String name = NAMES[index] + "\n" + (index == 0 ? "默认" : "")
                        + (index == active ? (index == 0 ? " · 已选" : "已选") : "");
                TextView caption = label(name, 14);
                caption.setMinLines(2);
                caption.setGravity(android.view.Gravity.CENTER);
                cell.addView(caption);
                cell.setContentDescription(name + "，预览壁纸");
                cell.setFocusable(true);
                cell.setOnClickListener(v -> preview(index, dialog));
            }
        }
        Button close = new Button(activity);
        close.setText("关闭");
        close.setOnClickListener(v -> dialog.dismiss());
        body.addView(close);
        dialog.setContentView(body);
        dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        dialog.getWindow().setLayout(Math.min(metrics.widthPixels - dp(24), dp(560)), (int) (metrics.heightPixels * .85f));
    }
    private void preview(int index, Dialog picker) {
        ImageView image = new ImageView(activity);
        image.setImageResource(IMAGES[index]);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        LinearLayout content = new LinearLayout(activity);
        int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * .55f);
        content.addView(image, new LinearLayout.LayoutParams(-1, height));
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle(NAMES[index]).setView(content).setNegativeButton("返回", null)
                .setPositiveButton("用于专注与睡眠", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(button -> {
            boolean saved = select(activity, index);
            if (!saved) {
                Toast.makeText(activity, "壁纸保存失败，请重试", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            picker.dismiss();
            Toast.makeText(activity, "专注与睡眠壁纸已更新", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(activity);
        view.setText(text); view.setTextSize(size);
        view.setTextColor(activity.getColor(R.color.text_primary));
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }
    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
}
