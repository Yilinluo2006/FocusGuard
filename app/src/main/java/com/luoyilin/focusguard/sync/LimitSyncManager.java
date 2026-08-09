package com.luoyilin.focusguard.sync;

import android.content.Context;
import android.content.SharedPreferences;

import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.AppLimitResponse;
import com.luoyilin.focusguard.network.NetworkErrorHelper;
import com.luoyilin.focusguard.network.RetrofitClient;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class LimitSyncManager {

    private static final String PREF_NAME = "focus_guard_prefs";
    // 必须与 AppManageActivity 和 FocusAccessibilityService 保持一致

    private static final String KEY_SELECTED_PACKAGES =
            "selected_packages";
    // 保存所有已启用限制的应用包名

    private static final String KEY_LIMIT_PREFIX =
            "limit_minutes_";
    // 保存每个应用限制时间时使用的 key 前缀

    private LimitSyncManager() {
    }
    // 禁止外部创建 LimitSyncManager 对象
    // 因为这个类只提供静态同步方法

    public interface SyncCallback {

        void onSuccess(int enabledCount);
        // 同步成功时返回已启用的限制数量

        void onFailure(String message);
        // 同步失败时返回错误信息
    }

    public static void syncFromServer(
            Context context,
            SyncCallback callback
    ) {
        Context applicationContext =
                context.getApplicationContext();
        // 使用 ApplicationContext，避免长期持有 Activity 或 Service

        SessionManager sessionManager =
                new SessionManager(applicationContext);

        if (!sessionManager.isLoggedIn()) {
            notifyFailure(callback, "登录状态已失效");
            return;
        }
        // 未登录时不向后端请求当前用户的限制数据

        long requestUserId = sessionManager.getUserId();
        // 记录发起请求时的用户，防止切换账号后写入旧账号数据

        RetrofitClient.getApi()
                .getAppLimits()
                .enqueue(new Callback<List<AppLimitResponse>>() {

                    @Override
                    public void onResponse(
                            Call<List<AppLimitResponse>> call,
                            Response<List<AppLimitResponse>> response
                    ) {
                        if (!isSameLoggedInUser(
                                applicationContext,
                                requestUserId
                        )) {
                            return;
                        }
                        // 用户已退出或切换账号时，丢弃旧请求的响应

                        if (!response.isSuccessful()
                                || response.body() == null) {

                            notifyFailure(
                                    callback,
                                    "服务器返回状态码："
                                            + response.code()
                            );
                            return;
                        }
                        // 响应不成功或没有响应内容时，不修改旧缓存

                        int enabledCount = saveToLocalCache(
                                applicationContext,
                                response.body()
                        );
                        // 将后端限制保存到 SharedPreferences

                        if (callback != null) {
                            callback.onSuccess(enabledCount);
                        }
                        // 通知调用者同步成功
                    }

                    @Override
                    public void onFailure(
                            Call<List<AppLimitResponse>> call,
                            Throwable throwable
                    ) {
                        if (!isSameLoggedInUser(
                                applicationContext,
                                requestUserId
                        )) {
                            return;
                        }
                        // 用户已退出或切换账号时，不再显示旧请求的错误提示

                        notifyFailure(
                                callback,
                                NetworkErrorHelper.getMessage(throwable)
                        );
                        // 把底层异常转换成用户能看懂的网络提示
                        // 网络失败时保留最后一次成功同步的本地缓存
                    }
                });
        // enqueue 表示异步请求，不会阻塞 Android 主线程
    }

    public static void clearLocalCache(Context context) {
        SharedPreferences preferences =
                context.getApplicationContext()
                        .getSharedPreferences(
                                PREF_NAME,
                                Context.MODE_PRIVATE
                        );
        // 打开保存应用限制数据的本地配置文件

        Set<String> oldPackages =
                preferences.getStringSet(
                        KEY_SELECTED_PACKAGES,
                        new HashSet<>()
                );
        // 读取当前账号缓存过的受限应用包名

        SharedPreferences.Editor editor = preferences.edit();

        if (oldPackages != null) {
            for (String packageName : oldPackages) {
                editor.remove(KEY_LIMIT_PREFIX + packageName);
            }
        }
        // 删除每个受限应用对应的本地限制时间

        editor.remove(KEY_SELECTED_PACKAGES);
        editor.apply();
        // 删除受限应用集合并异步保存修改
    }

    private static boolean isSameLoggedInUser(
            Context context,
            long requestUserId
    ) {
        SessionManager currentSession = new SessionManager(context);

        return currentSession.isLoggedIn()
                && currentSession.getUserId() == requestUserId;
        // 只有登录仍有效且用户 ID 未改变时，才允许处理同步结果
    }

    private static int saveToLocalCache(
            Context context,
            List<AppLimitResponse> limits
    ) {
        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );
        // 打开 FocusGuard 的本地配置文件

        Set<String> oldPackages =
                preferences.getStringSet(
                        KEY_SELECTED_PACKAGES,
                        new HashSet<>()
                );
        // 读取上一次同步的受限应用

        SharedPreferences.Editor editor =
                preferences.edit();
        // 创建 SharedPreferences 编辑器

        if (oldPackages != null) {
            for (String packageName : oldPackages) {
                editor.remove(
                        KEY_LIMIT_PREFIX + packageName
                );
            }
        }
        // 先删除上一次缓存的限制时间，防止保留已经关闭或删除的记录

        Set<String> enabledPackages =
                new HashSet<>();
        // 保存本次后端返回的已启用应用包名

        for (AppLimitResponse limit : limits) {
            if (!limit.isEnabled()) {
                continue;
            }
            // enabled=false 的记录不参与强制限制

            String packageName = limit.getPackageName();
            int limitMinutes = limit.getLimitMinutes();

            if (packageName == null
                    || packageName.trim().isEmpty()
                    || limitMinutes <= 0) {
                continue;
            }
            // 忽略包名为空或限制时间不合法的数据

            enabledPackages.add(packageName);
            // 将应用加入受限应用集合

            editor.putInt(
                    KEY_LIMIT_PREFIX + packageName,
                    limitMinutes
            );
            // 保存该应用对应的限制分钟数
        }

        editor.putStringSet(
                KEY_SELECTED_PACKAGES,
                enabledPackages
        );
        // 使用后端返回的启用记录替换旧的受限应用集合

        editor.apply();
        // 异步保存，本地内存中的数据会立即更新

        return enabledPackages.size();
        // 返回本次成功缓存的启用记录数量
    }

    private static void notifyFailure(
            SyncCallback callback,
            String message
    ) {
        if (callback != null) {
            callback.onFailure(message);
        }
        // callback 可能为空，因此调用前先判断
    }
}
