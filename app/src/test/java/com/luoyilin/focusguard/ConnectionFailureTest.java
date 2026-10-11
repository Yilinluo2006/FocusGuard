package com.luoyilin.focusguard;
import com.luoyilin.focusguard.network.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectionFailureTest {
    @Test public void noNetworkIsDifferentFromAnUnreachableServer(){
        assertEquals(ConnectionFailure.Kind.OFFLINE,ConnectionFailure.classify(new java.net.UnknownHostException(),true,false));
        assertEquals(ConnectionFailure.Kind.UNREACHABLE,ConnectionFailure.classify(new java.net.UnknownHostException(),false,false));
        assertEquals(ConnectionFailure.Kind.UNREACHABLE,ConnectionFailure.classify(new java.net.ConnectException(),false,false));
    }
    @Test public void usbLocalBackendDoesNotRequireInternet(){
        assertEquals(ConnectionFailure.Kind.UNREACHABLE,ConnectionFailure.classify(new java.net.ConnectException(),true,true));
    }
    @Test public void onlyConfirmed401MeansExpiredCredentials(){
        assertEquals(ConnectionFailure.Kind.AUTH_EXPIRED,ConnectionFailure.classify(new HttpFailure(401),true,false));
        assertEquals(ConnectionFailure.Kind.FORBIDDEN,ConnectionFailure.classify(new HttpFailure(403),false,false));
        assertEquals(ConnectionFailure.Kind.SERVER_ERROR,ConnectionFailure.classify(new HttpFailure(503),false,false));
    }
    @Test public void timeoutAndWrappedFailureRemainTransportErrors(){
        assertEquals(ConnectionFailure.Kind.TIMEOUT,ConnectionFailure.classify(new RuntimeException(new java.net.SocketTimeoutException()),false,false));
        assertEquals(ConnectionFailure.Kind.UNREACHABLE,ConnectionFailure.classify(new RuntimeException(new java.net.ConnectException()),false,false));
    }
    @Test public void messageNeverRequestsReloginForConnectionFailure(){
        assertTrue(ConnectionFailure.message(ConnectionFailure.Kind.UNREACHABLE).contains("无需重新登录"));
        assertTrue(ConnectionFailure.message(ConnectionFailure.Kind.TIMEOUT).contains("无需重新登录"));
        assertTrue(ConnectionFailure.message(ConnectionFailure.Kind.SERVER_ERROR).contains("无需重新登录"));
    }
}
