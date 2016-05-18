package com.baidu.simpleasynchttpclient;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

public class GenerateHttpClientUtils {

    
    public static HttpURLConnection getConnection(Context context, URL url) throws IOException{
        HttpURLConnection httpURLConnection = null;
        java.net.Proxy proxy = getProxy(context, url);
        if(proxy != null){
            httpURLConnection = (HttpURLConnection) url.openConnection(proxy);
            setCMWAPProperty(context, httpURLConnection, url.getHost());
        } else {
            httpURLConnection = (HttpURLConnection) url.openConnection();
        } 
        setConnectionParams(httpURLConnection); 
        return httpURLConnection;
    }
    
    
    private static void setCMWAPProperty(Context context, HttpURLConnection httpURLConnection, String host){
        String currentApnName = getCurrentApnInUse(context);
        if(!TextUtils.isEmpty(currentApnName) && currentApnName.startsWith( "CMWAP" )){
            httpURLConnection.setRequestProperty("X-Online-Host", host); 
            httpURLConnection.setDoInput(true); 
        } 
    }
    
    private static void setConnectionParams(HttpURLConnection httpURLConnection){ 
        final int OPERATION_TIMEOUT = 15000;
        httpURLConnection.setConnectTimeout(OPERATION_TIMEOUT);
        httpURLConnection.setReadTimeout(OPERATION_TIMEOUT);
        // 有可能有些会需要重定向
        HttpURLConnection.setFollowRedirects(true);
        httpURLConnection.setDoInput(true);  
        httpURLConnection.setDoOutput(true); 
        httpURLConnection.setUseCaches(false);
        
        httpURLConnection.setRequestProperty("accept", "*/*");  
        httpURLConnection.setRequestProperty("connection", "Keep-Alive");  
        httpURLConnection.setRequestProperty("ACCEPT-LANGUAGE", "zh-cn");
        httpURLConnection.setRequestProperty("ACCEPT-CHARSET", "UTF-8"); 
    }
    
    private static java.net.Proxy getProxy(Context context, URL url){ 
        String proxyHost = android.net.Proxy.getDefaultHost();  
        int proxyPort = android.net.Proxy.getDefaultPort();
        java.net.Proxy proxy = null;
        if (proxyHost != null) {
            proxy = new java.net.Proxy(
                    java.net.Proxy.Type.valueOf( url.getProtocol().toUpperCase() ), 
                    new InetSocketAddress(proxyHost, proxyPort));
            
        }   
        return proxy; 
    }
    
    /**获取当前网络名称
    * @param mcontext
    * @return
    */
    private static String getCurrentApnInUse(Context mcontext) {
        String name = "no";
        ConnectivityManager manager = (ConnectivityManager) mcontext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            NetworkInfo activeNetInfo = manager.getActiveNetworkInfo();
            if (activeNetInfo != null && activeNetInfo.isAvailable()) {
                name = activeNetInfo.getExtraInfo();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(TextUtils.isEmpty(name)){
            return null;
        }else{
            return name.toUpperCase();
        }
        
    }
    

}
