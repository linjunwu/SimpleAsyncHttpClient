package com.baidu.simpleasynchttpclient;

/**
 * HttpResponseException
 *
 * @author linjunwu
 * @since 2016/5/17
 */
public class HttpResponseException extends Exception {
    private int mStatusCode;
    private String mResponseBody;

    public HttpResponseException(int statusCode, String responseBody){
        super("http error-statusCode:" + statusCode + ",responseBody:" + responseBody);
        mStatusCode = statusCode;
        mResponseBody = responseBody;
    }

    public String getResponseBody() {
        return mResponseBody;
    }

    public int getStatusCode() {
        return mStatusCode;
    }
}
