package com.luoyilin.focusguard;

/** Display wording only; authoritative budgets and timer deadlines stay in their stores. */
final class QuotaPresentation {
    static final String CONTINUOUS_NOTE="每次 2 分钟连续计时，退出 App、切换应用或锁屏也不暂停。";
    static final String APP_POOL="本机所有受限应用每天共用 5 次应用临时使用，04:00 恢复。";
    static String appCount(int left){return left<0?"应用临时次数 · 暂不可用":"应用临时次数 · 剩余 "+left+" / 5";}
    static String appButton(int left){return left==0?"应用临时次数已用完 · 04:00 恢复":left<0?"应用临时次数暂不可用":"应用临时使用 2 分钟 · 剩余 "+left+" 次";}
    static String timer(long millis,boolean temporary,boolean offline,boolean shared){
        String title=temporary?"应用临时使用":offline?"离线已领取时间":shared?"共用每日额度":"本机每日额度";
        return title+"\n剩余 "+RemainingTimeFormat.format(millis);
    }
}
