package com.luoyilin.focusguard;

import android.content.Intent;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.BackupResponse;
import java.util.List;
import org.json.JSONObject;

public class AccountBackupActivity extends AppCompatActivity {
    private boolean busy;
    private CloudBackupManager.Task task;
    private retrofit2.Call<com.luoyilin.focusguard.network.ProfileResponse> profileCall;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this, 2000); }
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_account_backup);
        AccountInsets.apply(findViewById(R.id.accountBackupPage));
        findViewById(R.id.backupBack).setOnClickListener(v -> finish());
        androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!Appearance.dark(this));
        androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(!Appearance.dark(this));
        findViewById(R.id.backupSync).setOnClickListener(v -> startActivity(new Intent(this, DeviceSyncActivity.class)));
        findViewById(R.id.backupHelp).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this, R.style.HomeDialog).setTitle("备份与同步")
                        .setMessage("自动备份：保存本机设置与历史记录，用于恢复数据。手机与平板同步：在同一账号下同步配置；每台设备独立计算每日额度。\n\n开启备份后，应用限制、专注任务、睡前设置和已结束的记录会自动保存到当前账号。\n\n断网时保留本机数据，联网后重试。\n\n恢复时补充缺少的数据，保留已有本机设置。权限需要在本机开启，正在进行的计时不会从备份恢复。")
                        .setPositiveButton("知道了", null).show());
        findViewById(R.id.backupProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.backupAvatar).setOnClickListener(v -> {
            if (new SessionManager(this).isLoggedIn() && !busy) startActivity(new Intent(this, ProfileActivity.class));
        });
        findViewById(R.id.backupLogin).setOnClickListener(v -> {
            SessionManager session = new SessionManager(this);
            if (!session.isLoggedIn()) startActivity(new Intent(this, LoginActivity.class).putExtra("returnToBackup", true));
            else new MaterialAlertDialogBuilder(this, R.style.HomeDialog).setTitle("退出登录？").setMessage("暂停自动备份，保留所有本机设置和记录。")
                    .setNegativeButton("取消", null).setPositiveButton("退出登录", (d, w) -> {
                        CloudBackupManager.setEnabled(this, false); session.clearSession(); render();
                    }).show();
        });
        findViewById(R.id.backupToggle).setOnClickListener(v -> {
            if (CloudBackupManager.enabled(this)) { CloudBackupManager.setEnabled(this, false); render(); return; }
            render();
            new MaterialAlertDialogBuilder(this, R.style.HomeDialog).setTitle("开启自动备份？")
                    .setMessage("将本机应用限制、专注任务、睡前设置和历史记录保存到当前账号。更换账号后需重新开启。")
                    .setNegativeButton("取消", null).setPositiveButton("开启", (d, w) -> {
                        CloudBackupManager.setEnabled(this, true); sync();
                    }).show();
        });
        findViewById(R.id.backupNow).setOnClickListener(v -> sync());
        findViewById(R.id.backupRestore).setOnClickListener(v -> backups());
    }
    @Override protected void onResume() {
        super.onResume(); CloudBackupManager.reconcile(this); handler.post(refresh);
        SessionManager session = new SessionManager(this);
        if (session.isLoggedIn()) {
            String auth = session.getAuthorizationHeader();
            if (profileCall != null) profileCall.cancel();
            profileCall = com.luoyilin.focusguard.network.RetrofitClient.getApi().getProfile(auth);
            profileCall.enqueue(new AccountRequestCallback<>(session, auth, (profile, error) -> {
                if (!isFinishing() && !isDestroyed() && error == null) { session.updateProfile(profile, auth); render(); }
            }));
        }
    }
    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        if (profileCall != null) profileCall.cancel();
        super.onPause();
    }
    @Override protected void onDestroy() { if (task != null) task.cancel(); if (profileCall != null) profileCall.cancel(); super.onDestroy(); }
    private void render() {
        SessionManager account = new SessionManager(this); boolean loggedIn = account.isLoggedIn();
        AvatarCatalog.show(findViewById(R.id.backupAvatar), account.getAvatarKey(), 52);
        ((TextView)findViewById(R.id.backupAccount)).setText(loggedIn ? account.getUsername() : account.isSessionExpired() ? "登录已过期" : "未登录");
        ((TextView)findViewById(R.id.backupIdentity)).setText(loggedIn ? account.getLoginIdentifier() : account.isSessionExpired()?"重新登录后恢复同步，本机数据仍保留":"登录后备份数据、连接其他设备");
        findViewById(R.id.backupLogoutHint).setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        Button login = findViewById(R.id.backupLogin);
        login.setBackgroundResource(loggedIn ? R.drawable.account_row_background : R.drawable.button_rounded);
        login.setBackgroundTintList(loggedIn ? null : android.content.res.ColorStateList.valueOf(getColor(R.color.primary_button)));
        login.setTextColor(getColor(loggedIn ? R.color.text_secondary : R.color.on_primary));
        ((Button)findViewById(R.id.backupLogin)).setText(loggedIn ? "退出登录" : account.isSessionExpired()?"重新登录":"登录 / 注册");
        ((TextView)findViewById(R.id.backupStatus)).setText(CloudBackupManager.status(this));
        ((TextView)findViewById(R.id.backupTime)).setText(loggedIn ? CloudBackupManager.lastBackup(this) : "登录后可开启自动备份");
        findViewById(R.id.backupTime).setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        ((androidx.appcompat.widget.SwitchCompat)findViewById(R.id.backupToggle)).setChecked(CloudBackupManager.enabled(this));
        findViewById(R.id.backupNow).setVisibility(loggedIn && CloudBackupManager.enabled(this) ? View.VISIBLE : View.GONE);
        for (int id : new int[]{R.id.backupToggle, R.id.backupRestore, R.id.backupProfile}) findViewById(id).setEnabled(loggedIn && !busy);
        findViewById(R.id.backupNow).setEnabled(CloudBackupManager.enabled(this) && !busy);
        findViewById(R.id.backupLogin).setEnabled(!busy);
        findViewById(R.id.backupProgress).setVisibility(busy ? View.VISIBLE : View.GONE);
    }
    private void loading(boolean value) { busy = value; render(); }
    private void message(String value) { if (!isFinishing() && !isDestroyed()) Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private void sync() {
        loading(true);
        task = CloudBackupManager.sync(this, result -> {
            loading(false); message(result.error == null && Boolean.TRUE.equals(result.value) ? "备份已完成"
                    : result.error == null ? "自动备份已暂停" : result.error);
        });
    }
    private void backups() {
        loading(true);
        task = CloudBackupManager.list(this, result -> {
            loading(false);
            if (result.error != null) { message(result.error); return; }
            List<BackupResponse> values = result.value;
            if (values.isEmpty()) { message("当前账号暂无云端备份"); return; }
            String[] names = new String[values.size()];
            for (int i = 0; i < names.length; i++) {
                BackupResponse value = values.get(i); String time;
                try { time = java.time.Instant.parse(value.updatedAt).atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")); }
                catch (Exception invalid) { time = ""; }
                names[i] = value.deviceName + " · " + time;
            }
            new MaterialAlertDialogBuilder(this, R.style.HomeDialog).setTitle("选择设备备份").setItems(names, (d, w) -> download(values.get(w).deviceId))
                    .setNegativeButton("取消", null).show();
        });
    }
    private void download(String device) {
        loading(true);
        task = CloudBackupManager.download(this, device, result -> {
            loading(false);
            if (result.error != null) { message(result.error); return; }
            BackupResponse backup = result.value;
            try {
                JSONObject root = CloudBackupData.validate(backup.payload), focus = root.getJSONObject("focus");
                int records = focus.getJSONArray("focusHistory").length() + focus.getJSONArray("pomodoroHistory").length()
                        + focus.getJSONArray("sleepControlHistory").length();
                new MaterialAlertDialogBuilder(this, R.style.HomeDialog).setTitle("恢复 " + backup.deviceName)
                        .setMessage(root.getJSONArray("limits").length() + " 个应用限制 · " + root.getJSONArray("tasks").length()
                                + " 个任务 · " + records + " 条记录\n\n补充缺少的数据，保留已有本机设置。请先结束正在进行的专注或睡前管控。")
                        .setNegativeButton("取消", null).setPositiveButton("恢复", (d, w) -> {
                            loading(true);
                            task = CloudBackupManager.restore(this, backup, saved -> {
                                loading(false); message(saved.error == null ? "记录已恢复" : saved.error);
                            });
                        }).show();
            } catch (Exception invalid) { message("备份内容无法恢复"); }
        });
    }
}
