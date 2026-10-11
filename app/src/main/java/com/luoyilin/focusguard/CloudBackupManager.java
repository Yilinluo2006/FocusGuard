package com.luoyilin.focusguard;

import android.app.job.*;
import android.content.*;
import android.os.*;
import android.util.AtomicFile;
import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import retrofit2.Call;
import retrofit2.Response;

/** Main process only. Local protection never depends on this worker or the network. */
final class CloudBackupManager {
    static final int ON_CHANGE = 2110, PERIODIC = 2111;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("cloud_backup", 0); }
    static void initialize(Context c) {
        Context app = c.getApplicationContext();
        listener = (p, key) -> {
            if ("jwt_token".equals(key) || "user_id".equals(key)) { reconcile(app); return; }
            if (key == null || key.equals("selected_packages") || key.startsWith("limit_minutes_")
                    || key.equals("tasks") || key.equals("selected") || key.equals("daily")
                    || key.equals("minute") || key.equals("duration") || key.equals("allowed")
                    || key.endsWith("History")) {
                if (enabled(app)) prefs(app).edit().putString("status", "待备份 · 本机已保存").apply();
                request(app);
            }
        };
        for (String name : new String[]{"focus_guard_prefs", "focus_tasks", "focus_mode", "app_wallpaper", "focusguard_session"})
            app.getSharedPreferences(name, 0).registerOnSharedPreferenceChangeListener(listener);
        reconcile(app);
    }
    static boolean enabled(Context c) {
        SessionManager session = new SessionManager(c);
        SharedPreferences p = prefs(c);
        return session.isLoggedIn() && session.getUserId() > 0 && p.getBoolean("enabled", false)
                && session.getUserId() == p.getLong("owner", -1)
                && BuildConfig.API_BASE_URL.equals(p.getString("server", ""));
    }
    static synchronized void setEnabled(Context c, boolean enabled) {
        SessionManager session = new SessionManager(c);
        if (enabled && (!session.isLoggedIn() || session.getUserId() <= 0)) throw new IllegalStateException("请先登录");
        prefs(c).edit().putBoolean("enabled", enabled).putLong("owner", session.getUserId())
                .putString("server", BuildConfig.API_BASE_URL)
                .putString("status", enabled ? "等待自动备份" : "自动备份已关闭 · 本机数据保留").apply();
        reconcile(c);
    }
    static synchronized void reconcile(Context c) {
        JobScheduler jobs = c.getSystemService(JobScheduler.class);
        if (!enabled(c)) { jobs.cancel(ON_CHANGE); jobs.cancel(PERIODIC); return; }
        if (jobs.getPendingJob(PERIODIC) == null) jobs.schedule(builder(c, PERIODIC)
                .setPeriodic(15 * 60_000L).build());
        request(c);
    }
    static synchronized void request(Context c) {
        if (!enabled(c)) return;
        JobScheduler jobs = c.getSystemService(JobScheduler.class);
        if (jobs.getPendingJob(ON_CHANGE) == null) jobs.schedule(builder(c, ON_CHANGE)
                .setMinimumLatency(30_000).setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    private static JobInfo.Builder builder(Context c, int id) {
        return new JobInfo.Builder(id, new ComponentName(c, CloudBackupJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true);
    }
    static String status(Context c) {
        if (!new SessionManager(c).isLoggedIn()) return new SessionManager(c).isSessionExpired()
                ? "登录已过期 · 备份已暂停 · 本机数据保留" : "尚未登录 · 本机数据保留";
        if (!enabled(c)) {
            SharedPreferences p = prefs(c);
            if (new SessionManager(c).getUserId() == p.getLong("owner", -1)
                    && BuildConfig.API_BASE_URL.equals(p.getString("server", "")))
                return p.getString("status", "自动备份未开启 · 本机数据保留");
            return "自动备份未开启 · 本机数据保留";
        }
        SharedPreferences p = prefs(c);
        return p.getString("status", "等待自动备份");
    }
    static String lastBackup(Context c) {
        long time = prefs(c).getLong(key(c, "time"), 0);
        return time == 0 ? "尚未完成备份" : "最近备份 " + new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(time));
    }
    private static String key(Context c, String field) {
        // A revision belongs to both the account and this server, never to another account.
        return BuildConfig.API_BASE_URL + ":" + new SessionManager(c).getUserId() + ":" + field;
    }
    static synchronized String device(Context c) throws IOException {
        AtomicFile file = new AtomicFile(new File(c.getNoBackupFilesDir(), "cloud-device-id"));
        if (file.getBaseFile().exists()) {
            try (InputStream in = file.openRead()) {
                byte[] bytes = new byte[36]; int count = in.read(bytes);
                String value = new String(bytes, 0, Math.max(0, count), StandardCharsets.UTF_8);
                if (UUID.fromString(value).toString().equals(value)) return value;
            } catch (IllegalArgumentException invalid) { throw new IOException("设备备份编号损坏，请联系开发者", invalid); }
        }
        String value = UUID.randomUUID().toString(); FileOutputStream out = null;
        try { out = file.startWrite(); out.write(value.getBytes(StandardCharsets.UTF_8)); file.finishWrite(out); }
        catch (IOException e) { if (out != null) file.failWrite(out); throw e; }
        return value;
    }
    static final class Task {
        volatile boolean cancelled;
        volatile Call<?> call;
        synchronized void cancel() { cancelled = true; if (call != null) call.cancel(); }
        synchronized void attach(Call<?> value) throws IOException {
            call = value;
            if (cancelled) { value.cancel(); throw new IOException("操作已取消"); }
        }
    }
    static final class Result<T> {
        final T value; final String error; final boolean retry;
        Result(T v, String e, boolean r) { value = v; error = e; retry = r; }
    }
    private interface Action<T> { T run(Task task) throws Exception; }
    private static <T> Task submit(Action<T> action, Consumer<Result<T>> callback) {
        Task task = new Task();
        WORKER.execute(() -> {
            if (task.cancelled) return;
            Result<T> result;
            try { result = new Result<>(action.run(task), null, false); }
            catch (Exception e) {
                ConnectionFailure.Kind kind=NetworkEnvironment.classify(e);
                boolean retry=e instanceof IOException&&kind!=ConnectionFailure.Kind.AUTH_EXPIRED&&kind!=ConnectionFailure.Kind.FORBIDDEN&&kind!=ConnectionFailure.Kind.REQUEST_ERROR;
                result = new Result<>(null, e instanceof IOException?NetworkErrorHelper.getMessage(e):e.getMessage() == null ? "备份操作失败" : e.getMessage(), retry);
            }
            Result<T> delivered = result;
            MAIN.post(() -> { if (!task.cancelled) callback.accept(delivered); });
        });
        return task;
    }
    private static final class Account {
        final long id; final String authorization;
        Account(Context c) {
            SessionManager session = new SessionManager(c); id = session.getUserId(); authorization = session.getAuthorizationHeader();
        }
        boolean current(Context c) {
            SessionManager session = new SessionManager(c);
            return id == session.getUserId() && Objects.equals(authorization, session.getAuthorizationHeader());
        }
        void check(Context c, Task task) {
            if (id <= 0 || authorization == null) throw new IllegalStateException("请先登录");
            if (task.cancelled || !current(c)) throw new IllegalStateException("登录状态已变化，请重新操作");
        }
    }
    private static <T> T execute(Task task, Call<T> call) throws IOException {
        task.attach(call); Response<T> response = call.execute();
        if (!response.isSuccessful()) {
            int code = response.code();
            if (response.errorBody() != null) response.errorBody().close();
            if (code == 401) throw new HttpFailure(code);
            if (code == 409) throw new IllegalStateException("备份版本冲突，自动上传已暂停；请先恢复云端记录");
            if (code >= 500 || code == 408 || code == 429) throw new HttpFailure(code);
            throw new IllegalStateException("备份请求失败（" + code + "）");
        }
        if (response.body() == null) throw new IOException("备份服务返回为空，请重试");
        return response.body();
    }
    static Task sync(Context context, Consumer<Result<Boolean>> callback) {
        Context c = context.getApplicationContext();
        return submit(task -> {
            if (!enabled(c)) return false;
            Account account = new Account(c); SharedPreferences p = prefs(c);
            String hashKey = key(c, "hash"), revisionKey = key(c, "revision"), timeKey = key(c, "time");
            try {
            String payload = CloudBackupData.collect(c);
            String hash = BackupFingerprint.sha256(payload);
            if (hash.equals(p.getString(hashKey, ""))) { account.check(c, task); p.edit().putString("status", "自动备份已开启 · 已备份").apply(); return true; }
            account.check(c, task);
            String name = (Build.MANUFACTURER + " " + Build.MODEL).trim(); if (name.length() > 80) name = name.substring(0, 80);
                BackupResponse value = execute(task, RetrofitClient.getApi().putBackup(device(c), account.authorization,
                        new BackupRequest(name, p.getLong(revisionKey, 0), payload)));
                account.check(c, task);
                if (!enabled(c)) return false;
                if (!hash.equals(value.hash) || value.revision < 1) throw new IOException("备份校验失败，请重试");
                if (!p.edit().putString(hashKey, hash).putLong(revisionKey, value.revision).putLong(timeKey, System.currentTimeMillis())
                        .putString("status", "自动备份已开启 · 已备份").commit()) throw new IOException("备份状态保存失败，请重试");
                // Catch edits made during the request. The saved hash is the snapshot actually acknowledged.
                if (!hash.equals(BackupFingerprint.sha256(CloudBackupData.collect(c))))
                    MAIN.postDelayed(() -> request(c), 1000L);
                return true;
            } catch (Exception error) {
                if (!task.cancelled && account.current(c) && enabled(c)) {
                    String message = error instanceof IOException ? NetworkErrorHelper.getMessage(error)+" · 本机已保存" : error.getMessage();
                    SharedPreferences.Editor edit = p.edit().putString("status", message);
                    if (!(error instanceof IOException)) edit.putBoolean("enabled", false);
                    edit.apply();
                    if (!(error instanceof IOException)) reconcile(c);
                }
                throw error;
            }
        }, callback);
    }
    static Task list(Context context, Consumer<Result<List<BackupResponse>>> callback) {
        Context c = context.getApplicationContext(); Account account = new Account(c);
        return submit(task -> { account.check(c, task); List<BackupResponse> values = execute(task, RetrofitClient.getApi().getBackups(account.authorization)); account.check(c, task); return values; }, callback);
    }
    static Task download(Context context, String device, Consumer<Result<BackupResponse>> callback) {
        Context c = context.getApplicationContext(); Account account = new Account(c);
        return submit(task -> {
            account.check(c, task); BackupResponse value = execute(task, RetrofitClient.getApi().getBackup(device, account.authorization));
            account.check(c, task); CloudBackupData.validate(value.payload);
            if (!BackupFingerprint.sha256(value.payload).equals(value.hash)) throw new IllegalStateException("备份完整性校验失败");
            value.restoreOwner = account.id; value.restoreAuthorization = account.authorization;
            return value;
        }, callback);
    }
    static Task restore(Context context, BackupResponse backup, Consumer<Result<Boolean>> callback) {
        Context c = context.getApplicationContext(); Account account = new Account(c);
        return submit(task -> {
            account.check(c, task);
            if (backup.restoreOwner != account.id || !Objects.equals(backup.restoreAuthorization, account.authorization))
                throw new IllegalStateException("账号已变化，请重新下载备份");
            if (!BackupFingerprint.sha256(backup.payload).equals(backup.hash)) throw new IllegalStateException("备份完整性校验失败");
            Bundle args = new Bundle(); args.putString("payload", backup.payload);
            args.putLong("userId", account.id); args.putString("authorization", account.authorization);
            Bundle result = c.getContentResolver().call(LimitConfigProvider.URI, "restoreBackup", "focus_mode", args);
            if (result == null || !result.getBoolean("saved")) throw new IllegalStateException(result == null ? "恢复失败" : result.getString("error"));
            account.check(c, task);
            if (device(c).equals(backup.deviceId)) prefs(c).edit().putLong(key(c, "revision"), backup.revision).remove(key(c, "hash")).apply();
            request(c); return true;
        }, callback);
    }
}
