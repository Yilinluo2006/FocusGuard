package com.luoyilin.focusguard;

import android.content.Intent;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class ProtectionRecoveryActivity extends AppCompatActivity {
    private TextView state,records;private Button repair;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,1000);}};
    @Override protected void onCreate(Bundle saved){super.onCreate(saved);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(12),dp(20),dp(24));

        LinearLayout card=PageUi.card(this);state=text("");card.addView(state);repair=button("检查并恢复",this::repair);card.addView(repair);
        card.addView(button("查看三项保护状态  ›",()->startActivity(new Intent(this,ProtectionDashboardActivity.class))));PageUi.addCard(body,card);
        LinearLayout history=PageUi.card(this);history.addView(PageUi.heading(this,"恢复检查记录",18));records=text("");history.addView(records);
        history.addView(text("记录表示检查时的状态。整夜期间若服务没有持续反馈，该时段仍待核实；解锁时正常不能证明整夜都正常。"));PageUi.addCard(body,history);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(body);SecondaryPage.show(this,scroll,"恢复保护",this::finish);
        androidx.core.view.WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView()).setAppearanceLightStatusBars(!Appearance.dark(this));
    }
    @Override protected void onResume(){super.onResume();ProtectionRecovery.check(this,"app_resume");handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private void render(){SetupStatus s=new SetupStatus(this);ProtectionCapabilities p=ProtectionRecovery.capabilities(this);
        boolean restored=p.daily&&p.sleep&&p.focus;
        state.setText(restored?"三项保护能力已恢复。系统服务实际连接，设置与记录已保留。":ProtectionRecovery.explanation(this)
                +(s.accessibilityEnabled&&!s.accessibility?"\n\n进入系统页面，关闭再开启奶蛙无障碍服务，然后返回这里确认连接。":""));
        repair.setText(restored?"保护已恢复":!s.accessibility?"恢复无障碍服务":!s.usage?"开启使用记录":"检查必要设置");repair.setEnabled(!restored);
        records.setText(ProtectionRecoveryStore.summary(this)+(s.notifications?"":"\n\n通知尚未开启，离开应用时可能看不到保护中断提醒。请在必要设置中开启通知。"));
    }
    private void repair(){SetupStatus s=new SetupStatus(this);
        if(!s.accessibility)ProtectionRecovery.openAccessibility(this);
        else if(!s.usage)startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        else startActivity(new Intent(this,PermissionGuideActivity.class));
    }
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);PageUi.secondary(b);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);b.setLayoutParams(p);b.setOnClickListener(v->action.run());return b;}
    private TextView text(String value){TextView t=new TextView(this);t.setText(value);t.setTextSize(14);t.setTextColor(getColor(R.color.text_secondary));t.setPadding(0,dp(12),0,dp(12));return t;}
    private int dp(int value){return PageUi.dp(this,value);}
}
