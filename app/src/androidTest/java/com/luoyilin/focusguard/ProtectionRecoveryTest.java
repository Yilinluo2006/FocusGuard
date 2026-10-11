package com.luoyilin.focusguard;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Process;
import android.os.SystemClock;
import android.util.AtomicFile;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Uses private fixtures inside the isolated test app; never changes system permissions. */
public class ProtectionRecoveryTest {
    private Context c;
    private File fixtureDir;
    private int boot;

    private final ProtectionCapabilities full =
            new ProtectionCapabilities(true, true, true, true, false, false);
    private final ProtectionCapabilities wallpaperOnly =
            new ProtectionCapabilities(true, false, false, true, true, true);

    @Before public void setup() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(target.getPackageName().endsWith(".setupprobe"));
        fixtureDir = new File(target.getCacheDir(), "recovery-test-" + UUID.randomUUID());
        File files = new File(fixtureDir, "files");
        File noBackup = new File(fixtureDir, "no-backup");
        assertTrue(files.mkdirs());
        assertTrue(noBackup.mkdirs());
        c = new ContextWrapper(target) {
            @Override public File getFilesDir() { return files; }
            @Override public File getNoBackupFilesDir() { return noBackup; }
        };
        boot = ProtectionClock.boot(c);
        assumeTrue(boot >= 0);
    }

    @After public void cleanup() {
        if (fixtureDir != null) deleteFixture(fixtureDir);
    }

    @Test public void legacyHeartbeatWithoutBootMarkerCannotReportProtection() throws Exception {
        writeHeartbeat(1, boot, SystemClock.uptimeMillis());
        assertFalse(ProtectionStateStore.isAccessibilityHealthy(c));
        assertFalse(ProtectionStateStore.isProtectionHealthy(c));
    }

    @Test public void onlyCurrentBootCanAcceptFreshHeartbeat() throws Exception {
        writeHeartbeat(2, boot - 1, SystemClock.uptimeMillis());
        assertFalse(ProtectionStateStore.isAccessibilityHealthy(c));
        assertFalse(ProtectionStateStore.isProtectionHealthy(c));

        writeHeartbeat(2, boot, SystemClock.uptimeMillis());
        assertTrue(ProtectionStateStore.isAccessibilityHealthy(c));
        assertTrue(ProtectionStateStore.isProtectionHealthy(c));
    }

    @Test public void staleOrFutureHeartbeatCannotReportProtection() throws Exception {
        writeHeartbeat(2, boot, SystemClock.uptimeMillis() - 36_000);
        assertFalse(ProtectionStateStore.isAccessibilityHealthy(c));
        assertFalse(ProtectionStateStore.isProtectionHealthy(c));

        writeHeartbeat(2, boot, SystemClock.uptimeMillis() + 60_000);
        assertFalse(ProtectionStateStore.isAccessibilityHealthy(c));
        assertFalse(ProtectionStateStore.isProtectionHealthy(c));
    }

    @Test public void duplicateChecksAreCollapsedAndHistoryKeepsNewest24() throws Exception {
        ProtectionRecoveryStore.record(c, "app_resume", full);
        ProtectionRecoveryStore.record(c, "app_resume", full);
        assertEquals(1, ProtectionRecoveryStore.checks(c).length());

        for (int i = 0; i < 30; i++) ProtectionRecoveryStore.record(c, "fixture_" + i, full);
        JSONArray checks = ProtectionRecoveryStore.checks(c);
        assertEquals(24, checks.length());
        assertEquals("fixture_6", checks.getJSONObject(0).getString("cause"));
        assertEquals("fixture_29", checks.getJSONObject(23).getString("cause"));
    }

    @Test public void capabilityChangeIsRetainedEvenWhenCauseDoesNotChange() throws Exception {
        ProtectionRecoveryStore.record(c, "app_resume", full);
        ProtectionRecoveryStore.record(c, "app_resume", wallpaperOnly);
        JSONArray checks = ProtectionRecoveryStore.checks(c);
        assertEquals(2, checks.length());
        assertTrue(checks.getJSONObject(0).getBoolean("sleep"));
        JSONObject last = checks.getJSONObject(1);
        assertTrue(last.getBoolean("daily"));
        assertFalse(last.getBoolean("sleep"));
        assertFalse(last.getBoolean("focus"));
    }

    @Test public void lockedDurationDescribesUnlockCheckAndIsConsumedOnce() throws Exception {
        ProtectionRecoveryStore.lock(c);
        JSONObject fixture = readRecovery();
        long lockedAt = SystemClock.elapsedRealtime() - 65_000;
        fixture.put("lockedElapsed", lockedAt).put("lockedBoot", boot);
        writeRecovery(fixture);
        ProtectionRecoveryStore.lock(c);
        assertEquals(lockedAt, readRecovery().getLong("lockedElapsed"));

        ProtectionRecoveryStore.record(c, "unlock", wallpaperOnly);
        JSONObject unlock = ProtectionRecoveryStore.checks(c).getJSONObject(0);
        assertTrue(unlock.getLong("lockedMillis") >= 65_000);
        assertFalse(unlock.getBoolean("sleep"));
        assertFalse(unlock.getBoolean("focus"));
        assertTrue(ProtectionRecoveryStore.summary(c).contains("记录为解锁时检查结果"));
        assertFalse(readRecovery().has("lockedElapsed"));
        assertFalse(readRecovery().has("lockedBoot"));

        ProtectionRecoveryStore.record(c, "app_resume", full);
        assertEquals(0, ProtectionRecoveryStore.checks(c).getJSONObject(1).getLong("lockedMillis"));
    }

    @Test public void bootAndUpdateChecksDiscardOldLockAndPersistOnlyDiagnostics() throws Exception {
        ProtectionRecoveryStore.record(c, "app_resume", full);
        JSONObject fixture = readRecovery();
        fixture.put("lastBoot", boot - 1).put("lockedBoot", boot - 1)
                .put("lockedElapsed", SystemClock.elapsedRealtime() - 65_000);
        writeRecovery(fixture);
        ProtectionRecoveryStore.record(c, "app_resume", wallpaperOnly);
        JSONArray checks = ProtectionRecoveryStore.checks(c);
        JSONObject reboot = checks.getJSONObject(checks.length() - 1);
        assertEquals("boot", reboot.getString("cause"));
        assertEquals(0, reboot.getLong("lockedMillis"));

        fixture = readRecovery();
        fixture.put("lastUpdate", fixture.getLong("lastUpdate") - 1);
        writeRecovery(fixture);
        ProtectionRecoveryStore.record(c, "app_resume", full);
        checks = ProtectionRecoveryStore.checks(c);
        assertEquals("update", checks.getJSONObject(checks.length() - 1).getString("cause"));

        assertOnlyKeys(readRecovery(), "checks", "lastBoot", "lastUpdate");
        for (int i = 0; i < checks.length(); i++) {
            assertOnlyKeys(checks.getJSONObject(i), "cause", "boot", "elapsed", "at",
                    "lockedMillis", "daily", "sleep", "focus", "timeVerified");
        }
    }

    private void writeHeartbeat(int version, int marker, long uptime) throws Exception {
        AtomicFile file = new AtomicFile(new File(c.getFilesDir(), "protection_state.bin"));
        FileOutputStream out = file.startWrite();
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(version);
        data.writeBoolean(true);
        data.writeLong(uptime);
        data.writeBoolean(true);
        data.writeLong(uptime);
        data.writeInt(Process.myPid());
        data.writeUTF("isolated-fixture");
        data.writeUTF("heartbeat");
        if (version >= 2) data.writeInt(marker);
        data.flush();
        file.finishWrite(out);
    }

    private JSONObject readRecovery() throws Exception {
        File file = new File(c.getNoBackupFilesDir(), "protection-recovery.json");
        return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    private void writeRecovery(JSONObject root) throws Exception {
        AtomicFile file = new AtomicFile(new File(c.getNoBackupFilesDir(), "protection-recovery.json"));
        FileOutputStream out = file.startWrite();
        out.write(root.toString().getBytes(StandardCharsets.UTF_8));
        file.finishWrite(out);
    }

    private static void assertOnlyKeys(JSONObject value, String... allowedKeys) {
        Set<String> allowed = new HashSet<>(Arrays.asList(allowedKeys));
        Iterator<String> keys = value.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            assertTrue("Unexpected persisted field: " + key, allowed.contains(key));
        }
    }

    private static void deleteFixture(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteFixture(child);
        file.delete();
    }
}
