package com.luoyilin.focusguard;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.Gson;
import com.luoyilin.focusguard.auth.AuthInterceptor;
import com.luoyilin.focusguard.auth.SessionManager;
import com.luoyilin.focusguard.network.LoginResponse;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** All state changes are restricted to the independent instrumentation target. */
public class SyncStatusTest {
    private static final long LAST_SUCCESS = 1_790_000_000_000L;
    private static final String FAILURE = "服务器暂时不可达，请稍后重试；无需重新登录 · 本机额度正常可用";
    private static final Request REQUEST = new Request.Builder()
            .url("https://example.invalid/api/account/profile").build();
    private Context context;
    private SessionManager session;

    @Before public void reset() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(target.getPackageName().endsWith(".setupprobe"));
        // Status tests use cached failures independently of the device's network state.
        context = new ContextWrapper(target) {
            @Override public Object getSystemService(String name) {
                return CONNECTIVITY_SERVICE.equals(name) ? null : super.getSystemService(name);
            }
        };
        assertTrue(DeviceSyncManager.prefs(context).edit().clear().commit());
        session = new SessionManager(context);
        session.clearSession();
        login("initial-token");
    }

    @After public void clean() {
        if (context != null && context.getPackageName().endsWith(".setupprobe")) {
            assertTrue(DeviceSyncManager.prefs(context).edit().clear().commit());
            session.clearSession();
        }
    }

    @Test public void unauthorizedProtectedRequestExpiresTokenAndPreservesProfile() throws Exception {
        Response response = intercept(request -> {
            assertEquals("Bearer initial-token", request.header("Authorization"));
            return response(request, 401);
        });
        assertEquals(401, response.code());
        assertFalse(session.isLoggedIn());
        assertNull(session.getAuthorizationHeader());
        assertTrue(session.isSessionExpired());
        assertEquals(7L, session.getUserId());
        assertEquals("Test Account", session.getUsername());
        assertEquals("test@example.invalid", session.getEmail());
        assertEquals("default", session.getAvatarKey());
    }

    @Test public void newLoginAndExplicitLogoutClearExpiryMarker() {
        assertTrue(session.expireIfMatches(session.getAuthorizationHeader()));
        assertTrue(session.isSessionExpired());
        login("replacement-token");
        assertTrue(session.isLoggedIn());
        assertFalse(session.isSessionExpired());
        assertTrue(session.expireIfMatches(session.getAuthorizationHeader()));
        session.clearSession();
        assertFalse(session.isLoggedIn());
        assertFalse(session.isSessionExpired());
        assertEquals(-1L, session.getUserId());
        assertNull(session.getUsername());
    }

    @Test public void serviceUnavailableResponseRetainsLogin() throws Exception {
        assertEquals(503, intercept(request -> response(request, 503)).code());
        assertLoggedInWith("initial-token");
    }

    @Test public void connectionFailureRetainsLogin() throws Exception {
        ConnectException failure = new ConnectException("simulated unreachable server");
        try {
            intercept(request -> { throw failure; });
            fail("The transport failure must reach the caller");
        } catch (ConnectException actual) {
            assertSame(failure, actual);
        }
        assertLoggedInWith("initial-token");
    }

    @Test public void staleUnauthorizedResponseCannotExpireNewLogin() throws Exception {
        intercept(request -> {
            assertEquals("Bearer initial-token", request.header("Authorization"));
            login("newer-token");
            return response(request, 401);
        });
        assertLoggedInWith("newer-token");
        assertFalse(session.expireIfMatches("Bearer initial-token"));
        assertFalse(session.expireIfMatches(null));
        assertLoggedInWith("newer-token");
    }

    @Test public void unreachableStatusPreservesLastSuccessfulSync() {
        saveSyncState(true);
        assertEquals(FAILURE, DeviceSyncManager.status(context));
        String expected = "最后成功同步：" + new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(LAST_SUCCESS));
        assertEquals(expected, DeviceSyncManager.lastSuccess(context));
        assertEquals(LAST_SUCCESS, DeviceSyncManager.prefs(context).getLong("lastSuccessSync", 0));
        assertLoggedInWith("initial-token");
    }

    @Test public void disabledAndExpiredSyncOverrideCachedFailure() {
        saveSyncState(false);
        assertEquals("多设备同步已关闭", DeviceSyncManager.status(context));
        assertTrue(DeviceSyncManager.prefs(context).edit().putBoolean("enabled", true).commit());
        assertTrue(session.expireIfMatches(session.getAuthorizationHeader()));
        assertEquals("登录已过期 · 同步已暂停 · 本机额度正常可用", DeviceSyncManager.status(context));
        assertEquals(LAST_SUCCESS, DeviceSyncManager.prefs(context).getLong("lastSuccessSync", 0));
        assertTrue(DeviceSyncManager.prefs(context).edit().putBoolean("enabled", false).commit());
        assertEquals("多设备同步已关闭", DeviceSyncManager.status(context));
    }

    private void login(String token) {
        session.saveLoginSession(new Gson().fromJson("{\"userId\":7,\"token\":\"" + token
                + "\",\"tokenType\":\"Bearer\",\"displayName\":\"Test Account\","
                + "\"email\":\"test@example.invalid\",\"avatarKey\":\"default\"}", LoginResponse.class));
    }

    private void assertLoggedInWith(String token) {
        assertTrue(session.isLoggedIn());
        assertFalse(session.isSessionExpired());
        assertEquals("Bearer " + token, session.getAuthorizationHeader());
        assertEquals(7L, session.getUserId());
    }

    private void saveSyncState(boolean enabled) {
        SharedPreferences saved = DeviceSyncManager.prefs(context);
        assertTrue(saved.edit().putBoolean("enabled", enabled).putLong("owner", 7L)
                .putString("server", BuildConfig.API_BASE_URL).putString("statusKind", "UNREACHABLE")
                .putString("status", FAILURE).putLong("lastSuccessSync", LAST_SUCCESS).commit());
    }

    private Response intercept(Proceed proceed) throws IOException {
        Interceptor.Chain chain = (Interceptor.Chain) Proxy.newProxyInstance(
                Interceptor.Chain.class.getClassLoader(), new Class<?>[]{Interceptor.Chain.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("request")) return REQUEST;
                    if (method.getName().equals("proceed")) return proceed.run((Request) args[0]);
                    throw new UnsupportedOperationException(method.getName());
                });
        return new AuthInterceptor(context, session).intercept(chain);
    }

    private static Response response(Request request, int code) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("simulated response").build();
    }

    private interface Proceed { Response run(Request request) throws IOException; }
}
