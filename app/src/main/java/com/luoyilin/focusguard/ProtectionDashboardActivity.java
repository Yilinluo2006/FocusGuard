package com.luoyilin.focusguard;

import android.content.Intent;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class ProtectionDashboardActivity extends AppCompatActivity {
    private TextView summary,services;
    private final TextView[] states=new TextView[3],details=new TextView[3];
    private final Button[] actions=new Button[3];
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable update=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(12),dp(20),dp(24));
        summary=PageUi.heading(this,"检查中",16);summary.setPadding(0,dp(12),0,dp(4));body.addView(summary);
        TextView note=text("分别检查本机的三项保护。权限开关与实际服务连接同时满足，才显示就绪。",13);body.addView(note);
        String[] titles={"应用额度保护","睡眠保护","专注保护"};
        for(int i=0;i<3;i++){
            LinearLayout card=PageUi.card(this);LinearLayout top=new LinearLayout(this);top.setGravity(android.view.Gravity.CENTER_VERTICAL);
            top.addView(PageUi.heading(this,titles[i],17),new LinearLayout.LayoutParams(0,-2,1));states[i]=text("检查中",13);top.addView(states[i]);card.addView(top);
            details[i]=text("",14);details[i].setPadding(0,dp(12),0,dp(12));card.addView(details[i]);
            actions[i]=button("检查设置");card.addView(actions[i]);PageUi.addCard(body,card);
        }
        LinearLayout card=PageUi.card(this);card.addView(PageUi.heading(this,"运行链路",16));services=text("",13);services.setPadding(0,dp(8),0,0);card.addView(services);PageUi.addCard(body,card);
        body.addView(button("恢复保护与检查记录   ›"));
        ((Button)body.getChildAt(body.getChildCount()-1)).setOnClickListener(v->startActivity(new Intent(this,ProtectionRecoveryActivity.class)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(body);
        SecondaryPage.show(this,scroll,"保护状态",this::finish);
        androidx.core.view.WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView()).setAppearanceLightStatusBars(!Appearance.dark(this));
    }
    @Override protected void onResume(){super.onResume();handler.post(update);}
    @Override protected void onPause(){handler.removeCallbacks(update);super.onPause();}
    private void refresh(){
        ProtectionDashboardSnapshot snapshot=ProtectionDashboardSnapshot.read(this);
        summary.setText(snapshot.summary());services.setText(snapshot.services);
        ProtectionDashboardSnapshot.Row[] rows={snapshot.daily,snapshot.sleep,snapshot.focus};
        for(int i=0;i<3;i++){
            ProtectionDashboardSnapshot.Row row=rows[i];states[i].setText(row.status);states[i].setTextColor(getColor(row.good?R.color.accent_text:R.color.danger_text));
            details[i].setText(row.detail);actions[i].setText(row.action+"   ›");actions[i].setOnClickListener(v->open(row.destination));
        }
    }
    private void open(int target){startActivity(new Intent(this,target==ProtectionDashboardSnapshot.WALLPAPER?WallpaperGuardActivity.class
            :target==ProtectionDashboardSnapshot.APPS?AppManageActivity.class:target==ProtectionDashboardSnapshot.PLAN?FocusModeActivity.class:PermissionGuideActivity.class));}
    private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(getColor(R.color.text_secondary));return v;}
    private Button button(String title){Button v=new Button(this);v.setText(title);PageUi.secondary(v);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);v.setLayoutParams(p);return v;}
    private int dp(int value){return PageUi.dp(this,value);}
}
