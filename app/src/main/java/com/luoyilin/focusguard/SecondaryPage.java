package com.luoyilin.focusguard;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;

/** Fixed navigation and theme-aware system bars for secondary screens. */
final class SecondaryPage {
    static void show(AppCompatActivity activity, View content, String title, Runnable back) {
        androidx.activity.EdgeToEdge.enable(activity);
        if (content.getParent() instanceof ViewGroup) ((ViewGroup) content.getParent()).removeView(content);
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        androidx.appcompat.widget.Toolbar toolbar = new androidx.appcompat.widget.Toolbar(activity);
        toolbar.setTitle(title);
        toolbar.setTitleTextColor(activity.getColor(R.color.text_primary));
        toolbar.setNavigationIcon(R.drawable.ic_secondary_back);
        toolbar.setNavigationContentDescription("返回上一页");
        toolbar.setNavigationOnClickListener(v -> back.run());
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, PageUi.dp(activity, 56)));
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        AdaptivePageFrame frame = new AdaptivePageFrame(activity, page, 720);
        frame.setBackgroundColor(activity.getColor(R.color.page_background));
        activity.setContentView(frame);
        AccountInsets.apply(frame);
    }
}
