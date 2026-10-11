package com.luoyilin.focusguard;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuotaPresentationTest {
    @Test public void normalAllowanceAndTemporaryTimerCannotLookTheSame(){
        assertEquals("本机每日额度\n剩余 02:00",QuotaPresentation.timer(120000,false,false,false));
        assertEquals("应用临时使用\n剩余 02:00",QuotaPresentation.timer(120000,true,false,false));
    }
    @Test public void sharedLeaseNeverLooksLikeLocalDailyAllowance(){
        assertEquals("离线已领取时间\n剩余 02:00",QuotaPresentation.timer(120000,false,true,true));
        assertEquals("共用每日额度\n剩余 02:00",QuotaPresentation.timer(120000,false,false,true));
    }
    @Test public void zeroAndUnavailableAppCountsAreNotSleepGlobalCounts(){
        assertEquals("应用临时次数 · 剩余 0 / 5",QuotaPresentation.appCount(0));
        assertEquals("应用临时次数 · 暂不可用",QuotaPresentation.appCount(-1));
        assertTrue(QuotaPresentation.appButton(0).contains("04:00"));
    }
}
