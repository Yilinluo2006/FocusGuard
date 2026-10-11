package com.luoyilin.focusguard;

import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.NetworkErrorHelper;
import retrofit2.*;
import java.util.Objects;
import java.util.function.BiConsumer;

final class AccountRequestCallback<T> implements Callback<T> {
    private final SessionManager session;
    private final long owner;
    private final String authorization;
    private final BiConsumer<T, String> callback;
    AccountRequestCallback(SessionManager session, String authorization, BiConsumer<T, String> callback) {
        this.session = session; this.owner = session.getUserId(); this.authorization = authorization; this.callback = callback;
    }
    @Override public void onResponse(Call<T> call, Response<T> response) {
        if (call.isCanceled()) return;
        if (owner != session.getUserId() || (!Objects.equals(authorization, session.getAuthorizationHeader())
                && !(response.code()==401&&session.isSessionExpired()))) {
            callback.accept(null, "登录账号已变化，请重新操作"); return;
        }
        if (response.code() == 401) { callback.accept(null, "登录已过期，请重新登录"); return; }
        if (response.code()>=500||response.code()==408||response.code()==429) {
            callback.accept(null, NetworkErrorHelper.getMessage(new com.luoyilin.focusguard.network.HttpFailure(response.code())));return;
        }
        if (response.isSuccessful() && response.body() != null) { callback.accept(response.body(), null); return; }
        String error = "操作失败（" + response.code() + "）";
        if (response.errorBody() != null) try (okhttp3.ResponseBody body = response.errorBody()) {
            error = new org.json.JSONObject(body.string()).optString("message", error);
        } catch (Exception ignored) { }
        callback.accept(null, error);
    }
    @Override public void onFailure(Call<T> call, Throwable error) {
        if (!call.isCanceled()) callback.accept(null, NetworkErrorHelper.getMessage(error));
    }
}
