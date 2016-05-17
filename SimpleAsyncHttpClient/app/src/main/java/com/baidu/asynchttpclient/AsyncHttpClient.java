/*
 *  Android Asynchronous Http Client
 * Copyright (c) 2011 James Smith <james@loopj.com>
 *  http://loopj.com
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.baidu.asynchttpclient;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.SyncBasicHttpContext;

import android.content.Context;
import android.text.TextUtils;

/**
 * The AsyncHttpClient can be used to make asynchronous GET, POST, PUT and DELETE HTTP requests in your Android
 * applications. Requests can be made with additional parameters by passing a {@link RequestParams} instance, and
 * responses can be handled by passing an anonymously overridden {@link AsyncHttpResponseHandler} instance.
 * <p>
 * For example:
 * <p>
 * 
 * <pre>
 * AsyncHttpClient client = new AsyncHttpClient();
 * client.get(&quot;http://www.google.com&quot;, new AsyncHttpResponseHandler() {
 *     &#064;Override
 *     public void onSuccess(String response) {
 *         System.out.println(response);
 *     }
 * });
 * </pre>
 */
public class AsyncHttpClient {
    private static final String VERSION = "1.3.1";

    private static final int DEFAULT_MAX_CONNECTIONS = 15;
    private static final int DEFAULT_SOCKET_TIMEOUT = 15 * 1000;
    private static final int DEFAULT_MAX_RETRIES = 1;
    private static final int DEFAULT_SOCKET_BUFFER_SIZE = 8192;
    private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    private static final String ENCODING_GZIP = "gzip";

    private static int maxConnections = DEFAULT_MAX_CONNECTIONS;
    private static int socketTimeout = DEFAULT_SOCKET_TIMEOUT;

    private final DefaultHttpClient httpClient;
    private final HttpContext httpContext;
    private ThreadPoolExecutor threadPool;
    private final Map<Context, List<WeakReference<Future<?>>>> requestMap;
    private final Map<String, String> clientHeaderMap;

    /**
     * Creates a new AsyncHttpClient.
     */
    public AsyncHttpClient() {
        BasicHttpParams httpParams = new BasicHttpParams();

        ConnManagerParams.setTimeout(httpParams, socketTimeout);
        ConnManagerParams.setMaxConnectionsPerRoute(httpParams, new ConnPerRouteBean(maxConnections));
        ConnManagerParams.setMaxTotalConnections(httpParams, DEFAULT_MAX_CONNECTIONS);

        HttpConnectionParams.setSoTimeout(httpParams, socketTimeout);
        HttpConnectionParams.setConnectionTimeout(httpParams, socketTimeout);
        HttpConnectionParams.setTcpNoDelay(httpParams, true);
        HttpConnectionParams.setSocketBufferSize(httpParams, DEFAULT_SOCKET_BUFFER_SIZE);

        HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUserAgent(
                httpParams,
                String.format("android-async-http/%s (http://loopj.com/android-async-http)",
                VERSION));
        HttpProtocolParams.setUseExpectContinue(httpParams, false);

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        // schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
        ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(httpParams, schemeRegistry);

        httpContext = new SyncBasicHttpContext(new BasicHttpContext());
        httpClient = new DefaultHttpClient(cm, httpParams);
        httpClient.addRequestInterceptor(new HttpRequestInterceptor() {
            public void process(HttpRequest request, HttpContext context) {
                if (!request.containsHeader(HEADER_ACCEPT_ENCODING)) {
                    request.addHeader(HEADER_ACCEPT_ENCODING, ENCODING_GZIP);
                }
                for (String header : clientHeaderMap.keySet()) {
                    request.addHeader(header, clientHeaderMap.get(header));
                }
            }
        });

        httpClient.addResponseInterceptor(new HttpResponseInterceptor() {
            public void process(HttpResponse response, HttpContext context) {
                final HttpEntity entity = response.getEntity();
                final Header encoding = entity.getContentEncoding();
                if (encoding != null) {
                    for (HeaderElement element : encoding.getElements()) {
                        if (element.getName().equalsIgnoreCase(ENCODING_GZIP)) {
                            response.setEntity(new InflatingEntity(response.getEntity()));
                            break;
                        }
                    }
                }
            }
        });

        httpClient.setHttpRequestRetryHandler(new RetryHandler(DEFAULT_MAX_RETRIES));

        threadPool = (ThreadPoolExecutor) Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger mCount = new AtomicInteger(1);

            public Thread newThread(Runnable r) {

                Thread t = new Thread(r, "AsyncHttpClient #" + mCount.getAndIncrement());
                if (t.isDaemon())
                    t.setDaemon(false);
                if (t.getPriority() != (Thread.NORM_PRIORITY - 1))
                    t.setPriority((Thread.NORM_PRIORITY - 1));
                return t;
            }
        });

        requestMap = new WeakHashMap<Context, List<WeakReference<Future<?>>>>();
        clientHeaderMap = new HashMap<String, String>();
    }

    /**
     * Get the underlying HttpClient instance. This is useful for setting additional fine-grained settings for requests
     * by accessing the client's ConnectionManager, HttpParams and SchemeRegistry.
     */
    public HttpClient getHttpClient() {
        return this.httpClient;
    }

    /**
     * Sets an optional CookieStore to use when making requests
     * 
     * @param cookieStore The CookieStore implementation to use, usually an instance of {@link PersistentCookieStore}
     */
    public void setCookieStore(CookieStore cookieStore) {
        httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
    }

    /**
     * Overrides the threadpool implementation used when queuing/pooling requests. By default,
     * Executors.newCachedThreadPool() is used.
     * 
     * @param threadPool an instance of {@link ThreadPoolExecutor} to use for queuing/pooling requests.
     */
    public void setThreadPool(ThreadPoolExecutor threadPool) {
        this.threadPool = threadPool;
    }

    /**
     * Sets the User-Agent header to be sent with each request. By default,
     * "Android Asynchronous Http Client/VERSION (http://loopj.com/android-async-http/)" is used.
     * 
     * @param userAgent the string to use in the User-Agent header.
     */
    public void setUserAgent(String userAgent) {
        HttpProtocolParams.setUserAgent(this.httpClient.getParams(), userAgent);
    }

    /**
     * Sets the SSLSocketFactory to user when making requests. By default, a new, default SSLSocketFactory is used.
     * 
     * @param sslSocketFactory the socket factory to use for https requests.
     */
    public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
        this.httpClient.getConnectionManager().getSchemeRegistry().register(new Scheme("https", sslSocketFactory, 443));
    }

    /**
     * Sets headers that will be added to all requests this client makes (before sending).
     * 
     * @param header the name of the header
     * @param value the contents of the header
     */
    public void addHeader(String header, String value) {
        clientHeaderMap.put(header, value);
    }

    /**
     * Cancels any pending (or potentially active) requests associated with the passed Context.
     * <p>
     * <b>Note:</b> This will only affect requests which were created with a non-null android Context. This method is
     * intended to be used in the onDestroy method of your android activities to destroy all requests which are no
     * longer required.
     * 
     * @param context the android Context instance associated to the request.
     * @param mayInterruptIfRunning specifies if active requests should be cancelled along with pending requests.
     */
    public void cancelRequests(Context context, boolean mayInterruptIfRunning) {
        List<WeakReference<Future<?>>> requestList = requestMap.get(context);
        if (requestList != null) {
            for (WeakReference<Future<?>> requestRef : requestList) {
                Future<?> request = requestRef.get();
                if (request != null) {
                    request.cancel(mayInterruptIfRunning);
                }
            }
        }
        requestMap.remove(context);
    }

    //
    // HTTP GET Requests
    //

    /**
     * Perform a HTTP GET request, without any parameters.
     * 
     * @param url the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public WeakReference<Future<?>> get(String url, AsyncHttpResponseHandler responseHandler) {
        return get(null, url, null, responseHandler);
    }

    /**
     * Perform a HTTP GET request with parameters.
     * 
     * @param url the URL to send the request to.
     * @param params additional GET parameters to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public WeakReference<Future<?>> get(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        return get(null, url, params, responseHandler);
    }

    /**
     * Perform a HTTP GET request without any parameters and track the Android Context which initiated the request.
     * 
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public WeakReference<Future<?>> get(Context context, String url, AsyncHttpResponseHandler responseHandler) {
        return get(context, url, null, responseHandler);
    }

    /**
     * Perform a HTTP GET request and track the Android Context which initiated the request.
     * 
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param params additional GET parameters to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public WeakReference<Future<?>> get(Context context, String url, RequestParams params,
            AsyncHttpResponseHandler responseHandler) {
        return new WeakReference<Future<?>>(
                                        sendRequest(
                                                httpClient, httpContext, 
                                                new HttpGet(getUrlWithQueryString(url, params)),
                                                null, responseHandler, context));
    }

    public WeakReference<Future<?>> get(Context context, String url, RequestParams params, Map<String, String> headers,
            AsyncHttpResponseHandler responseHandler) {
        HttpGet getMethod = new HttpGet(getUrlWithQueryString(url, params));
        if (headers != null) {
            Set<Entry<String, String>> entrys = headers.entrySet();
            Iterator<Entry<String, String>> itertor = entrys.iterator();
            while (itertor.hasNext()) {
                Entry<String, String> entry = itertor.next();
                getMethod.addHeader(entry.getKey(), entry.getValue());
            }
        }

        return new WeakReference<Future<?>>(
                                        sendRequest(
                                                httpClient, httpContext, getMethod, null, responseHandler, context));
    }

    //
    // HTTP POST Requests
    //

    /**
     * Perform a HTTP POST request, without any parameters.
     * 
     * @param url the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public WeakReference<Future<?>> post(String url, AsyncHttpResponseHandler responseHandler) {
        return post(null, url, null, responseHandler);
    }

    /**
     * Perform a HTTP POST request with parameters.
     * 
     * @param url the URL to send the request to.
     * @param params additional POST parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public WeakReference<Future<?>> post(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        return post(null, url, params, responseHandler);
    }

    /**
     * Perform a HTTP POST request and track the Android Context which initiated the request.
     * 
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param params additional POST parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public WeakReference<Future<?>> post(Context context, String url, RequestParams params,
            AsyncHttpResponseHandler responseHandler) {
        return post(context, url, paramsToEntity(params), null, responseHandler);
    }

    /**
     * Perform a HTTP POST request and track the Android Context which initiated the request.
     * 
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param entity a raw {@link HttpEntity} to send with the request, for example, use this to send string/json/xml
     *            payloads to a server by passing a {@link org.apache.http.entity.StringEntity}.
     * @param contentType the content type of the payload you are sending, for example application/json if sending a
     *            json payload.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public WeakReference<Future<?>> post(Context context, String url, HttpEntity entity, String contentType,
            AsyncHttpResponseHandler responseHandler) {
        return new WeakReference<Future<?>>(
                                        sendRequest(
                                                httpClient, httpContext, 
                                                addEntityToRequestBase(new HttpPost(url), entity),
                                                contentType, responseHandler, context));
    }

    //
    // HTTP PUT Requests
    //

    /**
     * Perform a HTTP PUT request, without any parameters.
     * 
     * @param url the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public WeakReference<Future<?>> put(String url, AsyncHttpResponseHandler responseHandler) {
        return put(null, url, null, responseHandler);
    }

    /**
     * Perform a HTTP PUT request with parameters.
     * 
     * @param url the URL to send the request to.
     * @param params additional PUT parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public WeakReference<Future<?>> put(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        return put(null, url, params, responseHandler);
    }

    /**
     * Perform a HTTP PUT request and track the Android Context which initiated the request.
     * 
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param params additional PUT parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public WeakReference<Future<?>> put(Context context, String url, RequestParams params,
            AsyncHttpResponseHandler responseHandler) {
        return put(context, url, paramsToEntity(params), null, responseHandler);
    }

    /**
     * Perform a HTTP PUT request and track the Android Context which initiated the request.
     * 
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param entity a raw {@link HttpEntity} to send with the request, for example, use this to send string/json/xml
     *            payloads to a server by passing a {@link org.apache.http.entity.StringEntity}.
     * @param contentType the content type of the payload you are sending, for example application/json if sending a
     *            json payload.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public WeakReference<Future<?>> put(Context context, String url, HttpEntity entity, String contentType,
            AsyncHttpResponseHandler responseHandler) {
        return new WeakReference<Future<?>>(
                                         sendRequest(
                                                 httpClient, httpContext, 
                                                 addEntityToRequestBase(new HttpPut(url), entity),
                                                 contentType, responseHandler, context));
    }

    //
    // HTTP DELETE Requests
    //

    /**
     * Perform a HTTP DELETE request.
     * 
     * @param url the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public WeakReference<Future<?>> delete(String url, AsyncHttpResponseHandler responseHandler) {
        return delete(null, url, responseHandler);
    }

    /**
     * Perform a HTTP DELETE request.
     * 
     * @param context the Android Context which initiated the request.
     * @param url the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public WeakReference<Future<?>> delete(Context context, String url, AsyncHttpResponseHandler responseHandler) {
        final HttpDelete delete = new HttpDelete(url);
        return new WeakReference<Future<?>>(
                                        sendRequest(httpClient, httpContext, delete, null, responseHandler, context));
    }

    // Private stuff
    private Future<?> sendRequest(DefaultHttpClient client, HttpContext httpContext, HttpUriRequest uriRequest,
            String contentType, AsyncHttpResponseHandler responseHandler, Context context) {
        if (contentType != null) {
            uriRequest.addHeader("Content-Type", contentType);
        }
        /*
         * ------------------ 加http header ----------------- uriRequest.addHeader("Platform", "8"); // 平台类型 1=IOS
         * 8=Android uriRequest.addHeader("SDKVersion", NdCommplatformExtends.getInstance().getVersion()); // SDK的当前版本号
         * uriRequest.addHeader("FWVersion", Utils.getDeviceSDKVersion()); // 固件版本号 String model =
         * android.os.Build.MODEL; if(!TextUtils.isEmpty(model)) { if(model.length() > 10) { model =
         * model.substring(model.length() - 10); } uriRequest.addHeader("PhoneType", model); // 手机型号(如N97, Touch
         * HD等等，超过10个字符的取最后10个字符) } uriRequest.addHeader("Resolution", ScreenUtil.screen_width(context) + "x" +
         * ScreenUtil.screen_height(context)); // 分辨率，传入值为宽度x高度，如640x960 uriRequest.addHeader("Network",
         * Utils.isWifi(context)? "1" : "0"); // 网络类型 0=非WIFI 1=WIFI /* ------------------ 加http header
         * -----------------
         */

        Future<?> request = threadPool.submit(new AsyncHttpRequest(client, httpContext, uriRequest, responseHandler));

        if (context != null) {
            // Add request to request map
            List<WeakReference<Future<?>>> requestList = requestMap.get(context);
            if (requestList == null) {
                requestList = new LinkedList<WeakReference<Future<?>>>();
                requestMap.put(context, requestList);
            }

            requestList.add(new WeakReference<Future<?>>(request));

            // TODO: Remove dead weakrefs from requestLists?
        }
        return request;
    }

    private String getUrlWithQueryString(String url, RequestParams params) {
        if (params != null) {
            String paramString = params.getParamString();
            url += "?" + paramString;
        }

        return url;
    }

    private HttpEntity paramsToEntity(RequestParams params) {
        HttpEntity entity = null;

        if (params != null) {
            entity = params.getEntity();
        }

        return entity;
    }

    private HttpEntityEnclosingRequestBase addEntityToRequestBase(HttpEntityEnclosingRequestBase requestBase,
            HttpEntity entity) {
        if (entity != null) {
            requestBase.setEntity(entity);
        }

        return requestBase;
    }

    private static class InflatingEntity extends HttpEntityWrapper {
        public InflatingEntity(HttpEntity wrapped) {
            super(wrapped);
        }

        @Override
        public InputStream getContent() throws IOException {
            return new GZIPInputStream(wrappedEntity.getContent());
        }

        @Override
        public long getContentLength() {
            return -1;
        }
    }
}
