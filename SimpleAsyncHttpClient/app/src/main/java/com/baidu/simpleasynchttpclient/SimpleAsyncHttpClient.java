package com.baidu.simpleasynchttpclient;

import android.content.Context;
import android.os.AsyncTask;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

public class SimpleAsyncHttpClient<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {

    private static final String GET = "GET";
    private static final String POST = "POST";
    
    private Context mContext;
    private String mUrl;
    private AsyncHttpResponseHandler mAsyncHttpResponseHandler;
    private HttpURLConnection mHttpURLConnection;
    private String mRequestMethod = GET;
    
    private SimpleAsyncHttpClient(Context context, String url, 
            AsyncHttpResponseHandler asyncHttpResponseHandler, String requestMethod){
        mContext = context;
        mUrl = url;
        mAsyncHttpResponseHandler = asyncHttpResponseHandler;
        mRequestMethod = requestMethod;
    }
    /**
     * 释放httpURLConnection.
     * @param httpURLConnection
     */
    private void release(HttpURLConnection httpURLConnection) {
        if  (httpURLConnection != null) {
            httpURLConnection.disconnect();
            httpURLConnection = null;
        }
    }
    
    @Override
    protected void onPreExecute() {
        // TODO Auto-generated method stub
        super.onPreExecute();
        mAsyncHttpResponseHandler.sendStartMessage();
    }

    @Override
    protected void onPostExecute(Result result) {
        // TODO Auto-generated method stub
        super.onPostExecute(result);
        mAsyncHttpResponseHandler.sendFinishMessage();
    }

    @Override
    protected Result doInBackground(Params...paramArrayOfParams) {
        // TODO Auto-generated method stub
        try {
            URL url = new URL(mUrl);
            mHttpURLConnection = GenerateHttpClientUtils.getConnection(mContext, url);
            // todo 不知道为什么当在getConnection中把FollowRedirects设置为false，再此处再次设置为true没有效果？
            // HttpURLConnection.setFollowRedirects(true);
            mHttpURLConnection.setRequestMethod(mRequestMethod);
            mAsyncHttpResponseHandler.sendResponseMessage(mHttpURLConnection);
        } catch (IOException ioException) {
            // TODO: handle exception
            mAsyncHttpResponseHandler.sendFailureMessage(ioException, null);
        }
        release(mHttpURLConnection);
        return null;
    }
    
    public static <Params, Progress, Result> WeakReference<SimpleAsyncHttpClient<Params, Progress, Result>> getRequest(
            Context context, String url, 
           AsyncHttpResponseHandler asyncHttpResponseHandler){
        SimpleAsyncHttpClient<Params, Progress, Result> simpleAsyncHttpClient 
                = new SimpleAsyncHttpClient<Params, Progress, Result>(context, url, asyncHttpResponseHandler, GET);
        simpleAsyncHttpClient.execute((Params[])null);
        WeakReference<SimpleAsyncHttpClient<Params, Progress, Result>> wrSimpleAsyncHttpClient
                = new WeakReference<SimpleAsyncHttpClient<Params, Progress, Result>>(simpleAsyncHttpClient);
        return wrSimpleAsyncHttpClient;
    }
    
    public static <Params, Progress, Result> WeakReference<SimpleAsyncHttpClient<Params, Progress, Result>> postRequest(
            Context context, String url, 
            AsyncHttpResponseHandler asyncHttpResponseHandler){
        SimpleAsyncHttpClient<Params, Progress, Result> simpleAsyncHttpClient 
            = new SimpleAsyncHttpClient<Params, Progress, Result>(context, url, asyncHttpResponseHandler, POST);
        simpleAsyncHttpClient.execute((Params[])null);
        WeakReference<SimpleAsyncHttpClient<Params, Progress, Result>> wrSimpleAsyncHttpClient
            = new WeakReference<SimpleAsyncHttpClient<Params, Progress, Result>>(simpleAsyncHttpClient);
        return wrSimpleAsyncHttpClient;
    }

}
