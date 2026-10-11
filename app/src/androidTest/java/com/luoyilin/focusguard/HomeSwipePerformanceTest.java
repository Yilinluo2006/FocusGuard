package com.luoyilin.focusguard;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.viewpager.widget.ViewPager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Drives the actual pager callbacks without starting any focus or sleep session. */
@RunWith(AndroidJUnit4.class)
public class HomeSwipePerformanceTest {
    @Test public void firstAndRepeatSwipesDoNotBlockFrames() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            long worstCallback = 0;
            long worstFrame = 0;
            for (int target : new int[]{1, 2, 0, 1, 2}) {
                AtomicLong callbackMillis = new AtomicLong();
                AtomicLong frameMillis = new AtomicLong();
                CountDownLatch finished = new CountDownLatch(1);
                scenario.onActivity(activity -> {
                    ViewPager pager = activity.findViewById(R.id.homePager);
                    Choreographer choreographer = Choreographer.getInstance();
                    Choreographer.FrameCallback sampler = new Choreographer.FrameCallback() {
                        long previous;
                        @Override public void doFrame(long nanos) {
                            if (previous != 0) frameMillis.accumulateAndGet((nanos - previous) / 1_000_000, Math::max);
                            previous = nanos;
                            choreographer.postFrameCallback(this);
                        }
                    };
                    choreographer.postFrameCallback(sampler);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        long start = SystemClock.uptimeMillis();
                        // A drag ending beyond the midpoint invokes the same selection path as a finger swipe.
                        assertTrue(pager.beginFakeDrag());
                        float direction = target > pager.getCurrentItem() ? -1f : 1f;
                        int distance = Math.abs(target - pager.getCurrentItem());
                        pager.fakeDragBy(direction * pager.getWidth() * (distance - .2f));
                        pager.endFakeDrag();
                        callbackMillis.set(SystemClock.uptimeMillis() - start);
                    }, 60);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        choreographer.removeFrameCallback(sampler);
                        finished.countDown();
                    }, 800);
                });
                assertTrue("Pager did not settle", finished.await(5, TimeUnit.SECONDS));
                worstCallback = Math.max(worstCallback, callbackMillis.get());
                worstFrame = Math.max(worstFrame, frameMillis.get());
                android.os.Bundle metrics = new android.os.Bundle();
                metrics.putString("stream", "\nSwipe to " + target + ": callback=" + callbackMillis.get()
                        + " ms, longest frame=" + frameMillis.get() + " ms\n");
                InstrumentationRegistry.getInstrumentation().sendStatus(2, metrics);
            }
            long deadline = SystemClock.uptimeMillis() + 8_000;
            AtomicLong count = new AtomicLong();
            do {
                scenario.onActivity(activity -> {
                    RecyclerView list = activity.findViewById(R.id.rvAppList);
                    count.set(list == null || list.getAdapter() == null ? 0 : list.getAdapter().getItemCount());
                });
                if (count.get() > 0) break;
                SystemClock.sleep(40);
            } while (SystemClock.uptimeMillis() < deadline);
            assertTrue("Applications must load after the swipe", count.get() > 0);
            assertTrue("Swipe callback blocked the UI for " + worstCallback + " ms", worstCallback <= 48);
            assertTrue("Swipe dropped frames for " + worstFrame + " ms", worstFrame <= 100);
        }
    }
}
