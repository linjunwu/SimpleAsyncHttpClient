/*
 *Android Asynchronous Http Client
 * Copyright (c) 2011 James Smith <james@loopj.com>
 *http://loopj.com
 *Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 *http://www.apache.org/licenses/LICENSE-2.0
 *Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *limitations under the License.
 */

package com.baidu.simpleasynchttpclient;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

/**
 * Used to intercept and handle the responses from requests made using {@link SimpleAsyncHttpClient}. The
 * {@link #onSuccess(String)} method is designed to be anonymously overridden with your own response handling code.
 * <p>
 * Additionally, you can override the {@link #onFailure(Throwable, String)}, {@link #onStart()}, and {@link #onFinish()}
 * methods as required.
 * <p>
 * For example:
 * <p>
 * 
 * <pre>
 * AsyncHttpClient client = new AsyncHttpClient();
 * client.get(&quot;http://www.google.com&quot;, new AsyncHttpResponseHandler() {
 *     &#064;Override
 *     public void onStart() {
 *         // Initiated the request
 *     }
 * 
 *     &#064;Override
 *     public void onSuccess(String response) {
 *         // Successfully got a response
 *     }
 * 
 *     &#064;Override
 *     public void onFailure(Throwable e, String response) {
 *         // Response failed :(
 *     }
 * 
 *     &#064;Override
 *     public void onFinish() {
 *         // Completed the request (either success or failure)
 *     }
 * });
 * </pre>
 */
public class AsyncHttpResponseHandler {
    private static final String TAG = "AsyncHttpResponseHandler";

    private static final int BUFF_SIZE = 1024;
    private static final int SUCCESS_MESSAGE = 0;
    private static final int FAILURE_MESSAGE = 1;
    private static final int START_MESSAGE = 2;
    private static final int FINISH_MESSAGE = 3;

    private static final int RECEIVE_MESSAGE_START = 4;
    private static final int RECEIVE_MESSAGE_UPDATE = 5;
    private static final int RECEIVE_MESSAGE_END = 6;

    private Handler handler;

    /**
     * Creates a new AsyncHttpResponseHandler
     */
    public AsyncHttpResponseHandler() {
        // Set up a handler to post events back to the correct thread if possible
        if (Looper.myLooper() != null) {
            handler = new Handler() {
                public void handleMessage(Message msg) {
                    AsyncHttpResponseHandler.this.handleMessage(msg);
                }
            };
        }
    }

    public AsyncHttpResponseHandler(Handler h) {
        handler = h;
    }

    //
    // Callbacks to be overridden, typically anonymously
    //

    /**
     * Fired when the request is started, override to handle in your own code
     */
    public void onStart() {
        Log.i(TAG, "AsyncHttpResponseHandler:onStart");
    }

    /**
     * Fired in all cases when the request is finished, after both success and failure, override to handle in your own
     * code
     */
    public void onFinish() {
        Log.i(TAG, "AsyncHttpResponseHandler:onFinish");
    }

    /**
     * Fired when a request returns successfully, override to handle in your own code
     * 
     * @param content the body of the HTTP response from the server
     */
    public void onSuccess(String content) {}

    public void onStartReceive(int contentLength, String charset/* , Header[] headers */) {
    }

    public void onSegmentReceive(byte[] slice, int length) {
    }

    public void onSuccessReceive() {
    }

    /**
     * Fired when a request fails to complete, override to handle in your own code
     * 
     * @param error the underlying cause of the failure
     * @deprecated use {@link #onFailure(Throwable, String)}
     */
    public void onFailure(Throwable error) {

    }

    /**
     * Fired when a request fails to complete, override to handle in your own code
     * 
     * @param error the underlying cause of the failure
     * @param content the response body, if any
     */
    public void onFailure(Throwable error, String content) {
        // By default, call the deprecated onFailure(Throwable) for compatibility
        Log.i(TAG, "AsyncHttpResponseHandler:onFailure");
        onFailure(error);
    }

    //
    // Pre-processing of messages (executes in background threadpool thread)
    //

    protected void sendSuccessMessage(String responseBody) {
        sendMessage(obtainMessage(SUCCESS_MESSAGE, responseBody));
    }

    protected void sendFailureMessage(Throwable e, String responseBody) {
        sendMessage(obtainMessage(FAILURE_MESSAGE, new Object[] { e, responseBody }));
    }

    protected void sendStartMessage() {
        sendMessage(obtainMessage(START_MESSAGE, null));
    }

    protected void sendFinishMessage() {
        sendMessage(obtainMessage(FINISH_MESSAGE, null));
    }

    protected void sendReceiveStartMessage(int length, String charset/* , Header[] headers */) {
        Message msg = obtainMessage(RECEIVE_MESSAGE_START, null);
        // msg.obj = new Object[]{charset, headers};
        msg.obj = new Object[] { charset, null };
        msg.arg1 = length;
        sendMessage(msg);
    }

    protected void sendReceiveUpdateMessage(byte[] slice, int length) {
        Message msg = obtainMessage(RECEIVE_MESSAGE_UPDATE, slice);
        msg.arg1 = length;
        sendMessage(msg);
    }

    protected void sendReceiveEndMessage() {
        sendMessage(obtainMessage(RECEIVE_MESSAGE_END, null));
    }

    //
    // Pre-processing of messages (in original calling thread, typically the UI thread)
    //

    protected void handleSuccessMessage(String responseBody) {
        onSuccess(responseBody);
    }

    protected void handleFailureMessage(Throwable e, String responseBody) {
        onFailure(e, responseBody);
    }

    protected void handleReceiveStartMessage(int length, String charset/* , Header[] headers */) {
        onStartReceive(length, charset/* , headers */);
    }

    protected void handleReceiveUpdateMessage(byte[] slice, int length) {
        onSegmentReceive(slice, length);
    }

    protected void handleReceiveEndMessage() {
        onSuccessReceive();
    }

    // Methods which emulate android's Handler and Message methods
    protected void handleMessage(Message msg) {
        switch (msg.what) {
            case RECEIVE_MESSAGE_START: {
                Object[] repsonse = (Object[]) msg.obj;
                handleReceiveStartMessage(msg.arg1, (String) repsonse[0]/* , (Header[])repsonse[1] */);
            }
                break;

            case RECEIVE_MESSAGE_UPDATE:
                byte[] segment = (byte[]) msg.obj;
                handleReceiveUpdateMessage(segment, msg.arg1);
                break;
            case RECEIVE_MESSAGE_END:
                handleReceiveEndMessage();
                break;
            case SUCCESS_MESSAGE:
                handleSuccessMessage((String)msg.obj);
                break;
            case FAILURE_MESSAGE:
                Object[] repsonse = (Object[]) msg.obj;
                handleFailureMessage((Throwable) repsonse[0], (String) repsonse[1]);
                break;
            case START_MESSAGE:
                onStart();
                break;
            case FINISH_MESSAGE:
                onFinish();
                break;
        }
    }

    protected void sendMessage(Message msg) {
        if (handler != null) {
            Thread thread = handler.getLooper().getThread();
            if (thread.isAlive() && !thread.isInterrupted()) {
                handler.sendMessage(msg);
            }
        } else {
            handleMessage(msg);
        }
    }

    protected Message obtainMessage(int responseMessage, Object response) {
        Message msg = null;
        if (handler != null) {
            msg = this.handler.obtainMessage(responseMessage, response);
        } else {
            msg = new Message();
            msg.what = responseMessage;
            msg.obj = response;
        }
        return msg;
    }
    
    void sendResponseMessage(HttpURLConnection httpURLConnection) {

        try {
            InputStream in = httpURLConnection.getInputStream();
            int statusCode = httpURLConnection.getResponseCode();

            if (statusCode >= 300) {
                String responseBody = httpURLConnection.getResponseMessage();
                sendFailureMessage(
                        new HttpResponseException(statusCode, responseBody),
                        responseBody);
            } else {
                if (in == null) {
                    sendReceiveStartMessage(0, null);
                    sendReceiveUpdateMessage(new byte[0], 0);
                    sendReceiveEndMessage();
                    return;
                }
                // 第一步：读内容长度
                int contentLength = httpURLConnection.getContentLength();
                if (contentLength > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("HTTP entity too large to be buffered in memory");
                }

                if (contentLength < 0) {
                    contentLength = 4096;
                }
                String charset = "Receive Start";
                sendReceiveStartMessage(contentLength, charset);

                // 第二步：读内容
                boolean readDone = false;
                byte[] tmp = null;
                int remain = 0;
                // 从in输入流中循环读取数据，直到数据没有数据读取
                do {
                    if (Thread.currentThread().isInterrupted()) {
                        sendFailureMessage(new InterruptedException("request interupted!"), null);
                        return;
                    }
                    if (tmp == null) {
                        tmp = new byte[BUFF_SIZE];
                    }
                    int offset = 0;
                    remain = BUFF_SIZE;
                    // 从in输入流读取BUFF_SIZE字节数据，但是由于in.read读取的多少字节
                    //内容不一定与BUFF_SIZE一样，所以用了循环方式读取。
                    do {
                        if (Thread.currentThread().isInterrupted()) {
                            sendFailureMessage(new InterruptedException("request interupted!"), null);
                            return;
                        }
                        int length = in.read(tmp, offset, remain);
                        if (length != -1) {
                            offset += length;
                            remain -= length;
                        } else {
                            readDone = true;
                            break;
                        }
                    } while (remain > 0);

                    if (offset >= 0) {
                        sendReceiveUpdateMessage(tmp, offset);
                    }
                    tmp = null;
                } while (!readDone);
                // ////////////////////////phase 3//////////////////////////
                in.close();
                sendReceiveEndMessage();
            }

        } catch (IOException e) {
            sendFailureMessage(e, null);
        }
    }
}