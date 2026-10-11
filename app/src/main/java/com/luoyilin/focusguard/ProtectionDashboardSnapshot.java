package com.luoyilin.focusguard;

import android.content.Context;

final class ProtectionDashboardSnapshot {
    static final int PERMISSIONS=0,WALLPAPER=1,APPS=2,PLAN=3;
    static final class Row {
        final String title,status,detail,action;final boolean good;final int destination;
        Row(String t,String s,String d,boolean g,String a,int dest){title=t;status=s;detail=d;good=g;action=a;destination=dest;}
    }
    final ProtectionCapabilities capabilities;
    final Row daily,sleep,focus;
    final String services;
    String summary(){return "无法核实".equals(daily.status)||"无法核实".equals(sleep.status)||"无法核实".equals(focus.status)
            ?"部分保护状态待核实":capabilities.summary();}
    private ProtectionDashboardSnapshot(ProtectionCapabilities caps,Row daily,Row sleep,Row focus,String services){
        capabilities=caps;this.daily=daily;this.sleep=sleep;this.focus=focus;this.services=services;
    }
    static ProtectionDashboardSnapshot read(Context c) {
        SetupStatus setup=new SetupStatus(c);boolean selected=WallpaperGuard.selected(c);
        String monitor=WallpaperMonitorState.read(c);
        ProtectionCapabilities caps=new ProtectionCapabilities(setup.usage,setup.accessibilityEnabled,setup.accessibility,
                setup.overlay,selected,WallpaperMonitorState.ready(monitor));
        String accessibility=setup.accessibility?"无障碍已连接":setup.accessibilityEnabled?"无障碍开关已开，服务未连接":"无障碍未开启";
        String wallpaper=!selected?"壁纸守护未启用":!setup.usage||!setup.overlay?"壁纸守护权限不完整":WallpaperMonitorState.ERROR.equals(monitor)
                ?"壁纸监控异常":!WallpaperMonitorState.ready(monitor)?"壁纸监控未连接":"壁纸监控运行中";
        Row daily;
        try {
            int count=LimitSnapshot.read(c).size();
            if(!caps.daily)daily=new Row("应用额度保护","需要修复",!setup.usage?"使用记录权限未开启，无法核对每日额度。":
                    "没有可用的管控链路。恢复无障碍，或检查壁纸守护与浮窗权限。",false,
                    selected&&setup.usage?"检查壁纸守护":"检查必要设置",selected&&setup.usage?WALLPAPER:PERMISSIONS);
            else daily=new Row("应用额度保护",count==0?"未设置限制":caps.sleep?"保护已就绪":"备用保护中",
                    (count==0?"当前没有受限应用。":count+" 个受限应用 · 本机每日额度独立计算。")
                    +(caps.sleep?"通过无障碍执行。":"通过壁纸监控与浮窗执行；不包含睡眠和专注保护。"),true,"查看应用限制",APPS);
        }catch(java.io.IOException e){daily=new Row("应用额度保护","无法核实","应用限制配置读取失败，请检查必要设置后重试。",false,"检查必要设置",PERMISSIONS);}
        Row sleep,focus;
        try {
            FocusModeStore store=new FocusModeStore(c);android.os.Bundle temporary=SleepTemporaryUse.state(c);
            boolean sleeping=temporary.getBoolean("sleeping"),scheduled=store.prefs.getBoolean("daily",false);
            sleep=!caps.sleep?new Row("睡眠保护","需要修复",(scheduled?"睡前计划已开启，但":"")+"睡眠遮挡依赖无障碍实际连接，壁纸守护不能代替。",false,"恢复无障碍",PERMISSIONS)
                    :new Row("睡眠保护",!scheduled?"未开启计划":temporary.getLong("remaining")>0?"临时放行":sleeping?"正在保护":"就绪待命",
                    !scheduled?"保护能力已就绪，开启睡前计划后按时执行。":temporary.getLong("remaining")>0
                    ?"睡眠全局临时使用剩余 "+seconds(temporary.getLong("remaining"))+"，结束后回到睡眠模式。"
                    :sleeping?"睡眠全局临时次数：本次剩余 "+temporary.getInt("left")+" / 5。":"等待下一次睡前计划开始。",true,"查看睡前计划",PLAN);
            boolean active=store.pomodoro().getBoolean("active")||store.timed().getBoolean("active")||(!sleeping&&store.end(ProtectionClock.now(c))>0);
            focus=!caps.focus?new Row("专注保护","需要修复","专注遮挡依赖无障碍实际连接，壁纸守护不能代替。",false,"恢复无障碍",PERMISSIONS)
                    :new Row("专注保护",active?(sleeping?"睡眠优先执行":"正在保护"):"就绪待命",
                    active?(sleeping?"睡眠时段优先执行睡前管控。":"正在执行专注任务，非允许应用受到限制。")
                    :"当前没有进行中的专注任务，开始后执行保护。",true,"检查必要设置",PERMISSIONS);
        }catch(RuntimeException e){sleep=new Row("睡眠保护","无法核实","计划状态暂时读取失败。",false,"检查必要设置",PERMISSIONS);
            focus=new Row("专注保护","无法核实","专注状态暂时读取失败。",false,"检查必要设置",PERMISSIONS);}
        return new ProtectionDashboardSnapshot(caps,daily,sleep,focus,accessibility+"\n"+wallpaper+"\n"+(setup.protection?"后台保护服务运行中":"后台保护服务未运行"));
    }
    private static String seconds(long ms){long s=(ms+999)/1000;return String.format(java.util.Locale.getDefault(),"%02d:%02d",s/60,s%60);}
}
