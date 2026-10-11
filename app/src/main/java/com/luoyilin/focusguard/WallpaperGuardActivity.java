package com.luoyilin.focusguard;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AtomicFile;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.io.FileOutputStream;

public class WallpaperGuardActivity extends AppCompatActivity {
    private TextView status;
    private ImageView preview;
    private Button choose, enable, builtIn;
    private boolean importing;
    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) importImage(uri); });

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(12), dp(20), dp(24));

        TextView description = caption("无障碍断开时，继续检查受限应用的每日额度。需要使用记录与浮窗权限。专注、睡眠及页面识别仍需无障碍服务。");
        description.setPadding(0, dp(12), 0, dp(16)); body.addView(description);
        LinearLayout card = PageUi.card(this); status = caption(""); card.addView(status);
        preview = new ImageView(this); preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setContentDescription("守护壁纸预览"); card.addView(preview, new LinearLayout.LayoutParams(-1, dp(200)));
        card.addView(caption("新版 Android 无法自动读取现有壁纸。选择原图片可沿用相同画面；图片仅保存在本机。系统预览确认后才会更换桌面壁纸，原动态壁纸效果无法继承。"));
        builtIn = action("精选壁纸与纯色", this::showCatalog); card.addView(builtIn);
        choose = action("从相册选择图片", () -> picker.launch(new String[]{"image/*"})); card.addView(choose);
        enable = action("预览并开启壁纸守护", this::openPreview); card.addView(enable);
        PageUi.addCard(body, card);
        body.addView(action("使用记录与浮窗权限  ›", () -> startActivity(new Intent(this, PermissionGuideActivity.class))));
        body.addView(action("系统壁纸设置  ›", () -> open(new Intent(Intent.ACTION_SET_WALLPAPER))));
        ScrollView scroll = new ScrollView(this); scroll.addView(body); scroll.setFillViewport(true);
        SecondaryPage.show(this, scroll, "壁纸守护", this::finish);
        refresh();
    }
    @Override protected void onResume() { super.onResume(); refresh(); if (WallpaperGuard.available(this)) ProtectionService.start(this); }
    private void refresh() {
        if (status == null) return;
        status.setText(WallpaperGuard.selected(this) ? WallpaperGuard.permissions(this)
                ? "守护壁纸已启用 · 应用额度备用保护可用" : "守护壁纸已启用 · 请补全使用记录与浮窗权限"
                : "尚未启用 · 系统确认后生效");
        choose.setEnabled(!importing); enable.setEnabled(!importing); builtIn.setEnabled(!importing);
        if (WallpaperGuard.draft(this).exists() || WallpaperGuard.image(this).exists()) { preview.setImageDrawable(null); preview.setImageURI(Uri.fromFile(WallpaperGuard.draft(this).exists() ? WallpaperGuard.draft(this) : WallpaperGuard.image(this))); }
        else preview.setImageResource(AppWallpaper.selectedResource(this));
    }
    private void openPreview() {
        if (!WallpaperGuard.image(this).exists() && !WallpaperGuard.draft(this).exists()) {
            Toast.makeText(this, "请先选择要用作桌面壁纸的图片", Toast.LENGTH_LONG).show(); return;
        }
        if (!WallpaperGuard.permissions(this)) {
            Toast.makeText(this, "请先开启使用记录与浮窗权限", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, PermissionGuideActivity.class)); return;
        }
        if (WallpaperGuard.selected(this)) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this,R.style.HomeDialog)
                    .setTitle("应用这张守护壁纸？").setMessage("将预览图片应用到当前奶蛙守护壁纸，应用额度保护继续运行。")
                    .setNegativeButton("返回",null).setPositiveButton("确认应用",(d,w)->applyDraft(false)).show();
            return;
        }
        applyDraft(true);
    }
    private void applyDraft(boolean systemPreview) {
        try { GuardWallpaperImage.applyDraft(this); }
        catch (java.io.IOException error) { Toast.makeText(this,"壁纸保存失败，请重试",Toast.LENGTH_LONG).show();return; }
        if (!systemPreview) { Toast.makeText(this,"守护壁纸已更新",Toast.LENGTH_SHORT).show();refresh();return; }
        open(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(this, FocusWallpaperService.class)));
    }
    private void open(Intent intent) {
        try { startActivity(intent); } catch (RuntimeException e) { Toast.makeText(this, "系统未提供此入口，请到系统壁纸设置选择奶蛙守护壁纸", Toast.LENGTH_LONG).show(); }
    }
    private void importImage(Uri uri) {
        importing = true; refresh();
        new Thread(() -> {
            boolean success = false;
            try { GuardWallpaperImage.save(this, uri, WallpaperGuard.draft(this)); success = true; }
            catch (Exception e) { }
            final boolean saved = success;
            runOnUiThread(() -> { importing = false; if (!isFinishing() && !isDestroyed()) { refresh(); Toast.makeText(this, saved ? "图片已选择，请预览并确认应用" : "图片读取失败，请选择其他图片", Toast.LENGTH_LONG).show(); } });
        }, "wallpaper-import").start();
    }
    private void showCatalog() {
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(8),dp(16),dp(8));
        LinearLayout tabs=new LinearLayout(this);
        Button photos=action("精选壁纸",()->{}),colors=action("纯色",()->{});
        LinearLayout.LayoutParams tab=new LinearLayout.LayoutParams(0,dp(48),1);tabs.addView(photos,tab);
        LinearLayout.LayoutParams other=new LinearLayout.LayoutParams(0,dp(48),1);other.setMarginStart(dp(8));tabs.addView(colors,other);panel.addView(tabs);
        ScrollView scroll=new ScrollView(this);LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);scroll.addView(grid);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.round(getResources().getDisplayMetrics().heightPixels*0.48f)));
        androidx.appcompat.app.AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(this,R.style.HomeDialog)
                .setTitle("选择守护壁纸").setView(panel).setNegativeButton("返回",null).create();
        Runnable photoTab=()->{renderCatalog(grid,false,dialog);scroll.scrollTo(0,0);tintTab(photos,true);tintTab(colors,false);};
        Runnable colorTab=()->{renderCatalog(grid,true,dialog);scroll.scrollTo(0,0);tintTab(photos,false);tintTab(colors,true);};
        photos.setOnClickListener(v->photoTab.run());colors.setOnClickListener(v->colorTab.run());photoTab.run();dialog.show();
    }
    private void tintTab(Button button,boolean selected){button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(selected?R.color.primary_button:R.color.secondary_surface)));
        button.setTextColor(getColor(selected?R.color.on_primary:R.color.text_primary));}
    private void renderCatalog(LinearLayout grid,boolean solid,androidx.appcompat.app.AlertDialog dialog){
        grid.removeAllViews();int count=solid?GuardWallpaperCatalog.COLORS.length:AppWallpaper.count();
        for(int from=0;from<count;from+=2){
            LinearLayout row=new LinearLayout(this);
            for(int col=0;col<2;col++){
                int index=from+col;LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1);if(col>0)cell.setMarginStart(dp(10));
                if(index>=count){row.addView(new Space(this),cell);continue;}
                String name=solid?GuardWallpaperCatalog.COLOR_NAMES[index]:AppWallpaper.name(index);
                com.google.android.material.card.MaterialCardView tile=new com.google.android.material.card.MaterialCardView(this);
                tile.setRadius(dp(14));tile.setCardElevation(0);tile.setCardBackgroundColor(getColor(R.color.secondary_surface));
                LinearLayout contents=new LinearLayout(this);contents.setOrientation(LinearLayout.VERTICAL);
                ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                if(solid)image.setBackgroundColor(GuardWallpaperCatalog.COLORS[index]);
                else{
                    android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
                    android.graphics.BitmapFactory.decodeResource(getResources(),AppWallpaper.resource(index),options);options.inJustDecodeBounds=false;options.inSampleSize=1;options.inScaled=false;
                    while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>480)options.inSampleSize*=2;
                    image.setImageBitmap(android.graphics.BitmapFactory.decodeResource(getResources(),AppWallpaper.resource(index),options));
                }
                contents.addView(image,new LinearLayout.LayoutParams(-1,dp(solid?82:128)));TextView label=caption(name);label.setGravity(android.view.Gravity.CENTER);label.setTextColor(getColor(R.color.text_primary));contents.addView(label);
                tile.addView(contents);tile.setContentDescription(name+"，选择并预览");tile.setClickable(true);tile.setFocusable(true);
                tile.setOnClickListener(v->{dialog.dismiss();selectPreset(solid,index,name);});row.addView(tile,cell);
            }
            LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,-2);line.topMargin=dp(12);grid.addView(row,line);
        }
    }
    private void selectPreset(boolean solid,int index,String name){
        importing=true;refresh();new Thread(()->{
            boolean saved=false;try{if(solid)GuardWallpaperImage.solid(this,GuardWallpaperCatalog.COLORS[index]);else GuardWallpaperImage.builtIn(this,index);saved=true;}catch(Exception e){}
            final boolean success=saved;runOnUiThread(()->{importing=false;if(!isFinishing()&&!isDestroyed()){
                refresh();Toast.makeText(this,success?"已选择“"+name+"”，请确认应用":"壁纸读取失败，请重试",Toast.LENGTH_SHORT).show();
            }});
        },"wallpaper-preset").start();
    }
    private TextView caption(String text) { TextView v = new TextView(this); v.setText(text); v.setTextSize(14); v.setTextColor(getColor(R.color.text_secondary)); v.setPadding(0, dp(8), 0, dp(8)); return v; }
    private Button action(String text, Runnable click) {
        Button b = new Button(this); b.setText(text); PageUi.secondary(b); b.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12); b.setLayoutParams(params);
        return b;
    }
    private int dp(int value) { return PageUi.dp(this, value); }
}
