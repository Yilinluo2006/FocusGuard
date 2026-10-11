package com.luoyilin.focusguard.auth;

import android.content.Context;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {
    private final SessionManager sessionManager;

    public AuthInterceptor(Context context, SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        if (original.url().encodedPath().startsWith("/api/auth/")) {
            return chain.proceed(original);
        }
        String authorization = sessionManager.getAuthorizationHeader();
        Request request = original;
        if (authorization != null && original.header("Authorization") == null) {
            request = original.newBuilder().header("Authorization", authorization).build();
        }
        Response response = chain.proceed(request);
        if (response.code() == 401) {
            invalidateSession(request.header("Authorization"));
        }
        return response;
    }

    private synchronized void invalidateSession(String rejectedAuthorization) {
        sessionManager.expireIfMatches(rejectedAuthorization);
    }
}
