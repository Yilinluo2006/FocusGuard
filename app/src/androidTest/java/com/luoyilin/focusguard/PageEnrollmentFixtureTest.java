package com.luoyilin.focusguard;

import android.content.*;
import android.os.*;
import android.widget.*;
import androidx.core.content.ContextCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assume.assumeTrue;

/** Opt-in, disposable probe UI for exercising the formally bound recording service without real app data. */
public class PageEnrollmentFixtureTest {
    @Test public void fixtureForDeviceEnrollment() {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(c.getPackageName().endsWith(".setupprobe"));
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("pageEnrollmentFixture")));
        final TextView[] title = {null}; final Handler main = new Handler(Looper.getMainLooper());
        try (ActivityScenario<PageMonitoringActivity> s = ActivityScenario.launch(new Intent(c, PageMonitoringActivity.class))) {
            s.onActivity(a -> {
                LinearLayout page = new LinearLayout(a); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(60, 200, 60, 60);
                page.setBackgroundColor(a.getColor(R.color.page_background));
                title[0] = new TextView(a); title[0].setText("现场录入测试页"); title[0].setTextSize(28);
                title[0].setTextColor(a.getColor(R.color.text_primary)); title[0].setId(R.id.pageMonitoringConsent);
                title[0].setAccessibilityHeading(true); page.addView(title[0]);
                for (String label : new String[]{"打开相邻页面", "返回测试页面"}) {
                    Button b = new Button(a); b.setText(label);
                    b.setOnClickListener(v -> title[0].setText(label.equals("打开相邻页面") ? "相邻页面测试" : "现场录入测试页")); page.addView(b);
                }
                a.setContentView(page);
            });
            BroadcastReceiver changes = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    boolean adjacent = intent.getBooleanExtra("adjacent", false);
                    main.post(() -> title[0].setText(adjacent ? "相邻页面测试" : "现场录入测试页"));
                }
            };
            String action = c.getPackageName() + ".FIXTURE_PAGE";
            ContextCompat.registerReceiver(c, changes, new IntentFilter(action), ContextCompat.RECEIVER_EXPORTED);
            try { SystemClock.sleep(150_000); } finally { c.unregisterReceiver(changes); }
        }
    }
}
