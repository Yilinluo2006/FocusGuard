package com.luoyilin.focusguard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;

/** Navigates to protection management; checked state comes only from live capability checks. */
final class ProtectionStatusRow extends LinearLayout {
    private final TextView title;
    private final SwitchCompat indicator;
    ProtectionStatusRow(Context c) {
        super(c);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(14),dp(10),dp(14),dp(10));setMinimumHeight(dp(54));
        setBackgroundResource(R.drawable.page_action_background);
        title=new TextView(c);title.setTextSize(15);title.setTextColor(c.getColor(R.color.text_primary));
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(title,new LayoutParams(0,-2,1));
        indicator=new SwitchCompat(c);
        indicator.setShowText(false);indicator.setSwitchMinWidth(dp(44));
        indicator.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},
                new int[]{c.getColor(R.color.on_primary),c.getColor(R.color.text_secondary)}));
        indicator.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},
                new int[]{c.getColor(R.color.primary_button),c.getColor(R.color.divider)}));
        indicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        indicator.setClickable(false);indicator.setFocusable(false);
        indicator.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP)performClick();
            return true;
        });
        addView(indicator,new LayoutParams(-2,dp(36)));
        setClickable(true);setFocusable(true);
        LayoutParams p=new LayoutParams(-1,-2);p.topMargin=dp(8);setLayoutParams(p);
    }
    void bind(ProtectionDashboardSnapshot.Row row,Runnable manage) {
        title.setText(row.title);indicator.setChecked(row.good);
        String availability=row.good?"保护可用":"无法核实".equals(row.status)?"待核实":"需要修复";
        setContentDescription(row.title+"，"+availability+"，"+row.status+"，点击管理");
        androidx.core.view.ViewCompat.setStateDescription(this,availability);
        setOnClickListener(v->manage.run());
    }
    private int dp(int value){return PageUi.dp(getContext(),value);}
}
