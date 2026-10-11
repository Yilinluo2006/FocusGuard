package com.luoyilin.focusguard.network;

import android.content.Context;
import android.net.ConnectivityManager;
import com.luoyilin.focusguard.BuildConfig;

public final class NetworkEnvironment {
    private static Context application;
    static void initialize(Context c){application=c.getApplicationContext();}
    public static boolean localEndpoint(){try{String host=java.net.URI.create(BuildConfig.API_BASE_URL).getHost();return "127.0.0.1".equals(host)||"localhost".equalsIgnoreCase(host)||"::1".equals(host);}catch(Exception e){return false;}}
    public static boolean offline(){return offline(application);}
    public static boolean offline(Context c){if(c==null)return false;try{ConnectivityManager m=c.getSystemService(ConnectivityManager.class);return m!=null&&m.getActiveNetwork()==null;}catch(RuntimeException e){return false;}}
    public static ConnectionFailure.Kind classify(Throwable t){return ConnectionFailure.classify(t,offline(),localEndpoint());}
}
