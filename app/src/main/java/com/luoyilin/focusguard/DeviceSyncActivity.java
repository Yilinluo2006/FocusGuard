package com.luoyilin.focusguard;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DeviceSyncActivity extends androidx.appcompat.app.AppCompatActivity {
    private TextView status,lastSuccess;private Button toggle,now;private LinearLayout conflict;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable update=new Runnable(){@Override public void run(){render();handler.postDelayed(this,1000);}};
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);androidx.activity.EdgeToEdge.enable(this);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int p=(int)(20*getResources().getDisplayMetrics().density);body.setPadding(p,p,p,p);
        LinearLayout card=PageUi.card(this);PageUi.addCard(body,card);
        status=PageUi.heading(this,"",16);card.addView(status);
        lastSuccess=new TextView(this);lastSuccess.setTextSize(13);lastSuccess.setTextColor(getColor(R.color.text_secondary));
        lastSuccess.setPadding(0,PageUi.dp(this,10),0,0);card.addView(lastSuccess);
        TextView text=new TextView(this);text.setText("专注任务、应用限制与睡前计划自动同步。\n\n"
                +(DeviceSyncManager.strictQuota(this)?"两端共用每日额度。断网时只能用本机已领取的时间。":"每台设备独立计算每日额度，离线也能继续使用。例如设置 30 分钟，手机和平板各有 30 分钟。")
                +"\n\n凌晨 04:00 重置。联网后汇总两端用时，可在统计页分别查看。系统权限需要各自开启。");text.setTextSize(15);text.setTextColor(getColor(R.color.text_secondary));text.setPadding(0,p,0,p);card.addView(text);
        toggle=button(card,"",()->{
            boolean disabling=DeviceSyncManager.enabled(this);
            new MaterialAlertDialogBuilder(this,R.style.HomeDialog).setTitle(disabling?"关闭多设备同步？":"开启多设备同步？")
                    .setMessage(disabling?"后续修改不再同步到其他设备；当前设置和今天已用时间会保留。":"将专注任务、应用限制、睡前计划和应用用时同步到当前账号。每台设备独立计算每日额度，今天已用时间会保留。")
                    .setNegativeButton("取消",null).setPositiveButton(disabling?"关闭":"开启",(d,w)->{try{DeviceSyncManager.setEnabled(this,!disabling);render();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}).show();
        });toggle.setId(R.id.deviceSyncToggle);
        now=button(card,"立即同步",()->{
            if(!new com.luoyilin.focusguard.auth.SessionManager(this).isLoggedIn())startActivity(new android.content.Intent(this,LoginActivity.class).putExtra("returnToBackup",true));
            else DeviceSyncManager.tick(this);
        });now.setId(R.id.deviceSyncNow);
        conflict=new LinearLayout(this);conflict.setOrientation(LinearLayout.VERTICAL);card.addView(conflict);
        button(conflict,"保留本机修改",()->resolve(true));button(conflict,"使用云端设置",()->resolve(false));

        ScrollView scroll=new ScrollView(this);scroll.addView(body);SecondaryPage.show(this,scroll,"设备同步",this::finish);
    }
    private void resolve(boolean local){new MaterialAlertDialogBuilder(this,R.style.HomeDialog).setTitle(local?"保留本机修改？":"使用云端设置？").setMessage("将处理同时修改的专注任务、应用限制或睡前计划；正在执行的睡前管控结束后再应用新计划。")
            .setNegativeButton("取消",null).setPositiveButton("确认",(d,w)->DeviceSyncManager.resolve(this,local)).show();}
    private Button button(LinearLayout parent,String label,Runnable action){
        Button button=new androidx.appcompat.widget.AppCompatButton(this);
        button.setText(label);button.setAllCaps(false);PageUi.secondary(button);button.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=PageUi.dp(this,"返回".equals(label)?16:12);
        parent.addView(button,params);return button;
    }
    private void render(){status.setText(DeviceSyncManager.status(this));lastSuccess.setText(DeviceSyncManager.lastSuccess(this));
        toggle.setText(DeviceSyncManager.enabled(this)?"关闭多设备同步":"开启多设备同步");
        boolean logged=new com.luoyilin.focusguard.auth.SessionManager(this).isLoggedIn();
        now.setText(!logged?"登录以恢复同步":"立即同步");now.setEnabled(!logged||DeviceSyncManager.enabled(this));
        conflict.setVisibility(DeviceSyncManager.prefs(this).getBoolean("conflict",false)?View.VISIBLE:View.GONE);
        for(int i=0;i<conflict.getChildCount();i++)conflict.getChildAt(i).setEnabled(logged);
    }
    @Override protected void onResume(){super.onResume();handler.post(update);}
    @Override protected void onPause(){handler.removeCallbacks(update);super.onPause();}
}
