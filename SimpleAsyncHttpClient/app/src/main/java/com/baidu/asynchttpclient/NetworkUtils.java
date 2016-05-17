package com.baidu.asynchttpclient;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.util.InetAddressUtils;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;

import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class NetworkUtils {
    
    private static WeakReference<Context> mContext;

    public static void refreshProxySetting(Context context, HttpClient client) {
        context = updateContext(context);
        HttpParams params = client.getParams();
        if (isProxyNetwork(context)) {
            String proxy = android.net.Proxy.getDefaultHost();
            int port = android.net.Proxy.getDefaultPort();
            HttpHost host = new HttpHost(proxy, port);
            params.setParameter(ConnRouteParams.DEFAULT_PROXY, host);
        } else {
            params.removeParameter(ConnRouteParams.DEFAULT_PROXY);
        }
    }

    public static HttpClient newHttpClient() {
        HttpParams params = new BasicHttpParams();

        final int operationTimeout = 15000;

        ConnManagerParams.setTimeout(params, operationTimeout);
        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRoute() {

            @Override
            public int getMaxForRoute(HttpRoute route) {
                int result = 5;
                if (result >= ConnPerRouteBean.DEFAULT_MAX_CONNECTIONS_PER_ROUTE) {
                    return result;
                }
                return ConnPerRouteBean.DEFAULT_MAX_CONNECTIONS_PER_ROUTE;
            }

        });
        HttpConnectionParams.setConnectionTimeout(params, operationTimeout);
        HttpConnectionParams.setSoTimeout(params, operationTimeout);

        HttpClientParams.setRedirecting(params, true);
        HttpClientParams.setCookiePolicy(params, CookiePolicy.BROWSER_COMPATIBILITY);
        HttpProtocolParams.setUseExpectContinue(params, false);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
        HttpConnectionParams.setTcpNoDelay(params, true);
        HttpConnectionParams.setStaleCheckingEnabled(params, true);

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        DefaultHttpClient defaultHttpClient = new DefaultHttpClient(
                new ThreadSafeClientConnManager(params, schemeRegistry), params);
        defaultHttpClient.setKeepAliveStrategy(
                new DefaultConnectionKeepAliveStrategy() {

                    @Override
                    public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
                        long timeout = super.getKeepAliveDuration(response, context);
                        if (timeout == -1) {
                            // 服务器未指明时间，默认30s
                            return 30 * 1000;
                        }
                        return timeout;
                    }

                });
        return defaultHttpClient;
    }

    private static boolean isProxyNetwork(Context context) {
        if (context == null) {
            return false;
        }
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null || networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            return false;
        }
        if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
            if (!TextUtils.isEmpty(android.net.Proxy.getDefaultHost()) && android.net.Proxy.getDefaultPort() != -1) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNetActive(Context context) {
        context = updateContext(context);
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if (info != null && info.isAvailable()) {
            return true;
        }
        return false;
    }

    public static boolean isWifiActive(Context context) {
        ConnectivityManager mConnMgra = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfowifi = mConnMgra.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return networkInfowifi.isAvailable() && networkInfowifi.isConnected();
    }

    /**
     * 获取本地IP
     * 
     * @return
     */
    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        String ip = inetAddress.getHostAddress();
                        if (InetAddressUtils.isIPv4Address(ip)) {
                            return ip;
                        }
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * 有些调用方没有context参数
     * 
     * @param context
     */
    private static Context updateContext(Context context) {
        if (context != null) {
            mContext = new WeakReference<Context>(context.getApplicationContext());
        } else if (mContext != null) {
            context = mContext.get();
        }
        return context;
    }

}
