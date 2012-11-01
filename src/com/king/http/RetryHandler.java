package com.king.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;

import javax.net.ssl.SSLHandshakeException;

import org.apache.http.NoHttpResponseException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import android.os.SystemClock;

class RetryHandler implements HttpRequestRetryHandler {
    private static final int RETRY_SLEEP_TIME_MILLIS = 1500;
    private static HashSet<Class<?>> exceptionWhitelist = new HashSet<Class<?>>();
    private static HashSet<Class<?>> exceptionBlacklist = new HashSet<Class<?>>();

    static {
    	/**
    	 * exceptionWhitelist 表示碰到这些异常 --需要重连--
    	 */
        // 服务器可能有问题
        exceptionWhitelist.add(NoHttpResponseException.class);
        // 以下两个异常 可能是wifi 间断性端开
        exceptionWhitelist.add(UnknownHostException.class);
        exceptionWhitelist.add(SocketException.class);

    	/**
    	 * exceptionBlacklist 表示碰到这些异常 --不再重连--
    	 */
        exceptionBlacklist.add(InterruptedIOException.class);
        exceptionBlacklist.add(SSLHandshakeException.class);
    }

    private final int maxRetries;

    public RetryHandler(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
        boolean retry = true;

        Boolean b = (Boolean) context.getAttribute(ExecutionContext.HTTP_REQ_SENT);
        boolean sent = (b != null && b.booleanValue());

        if(executionCount > maxRetries) {
            retry = false;
        } else if (exceptionBlacklist.contains(exception.getClass())) {
        	// 黑名单 不在重连
            retry = false;
        } else if (exceptionWhitelist.contains(exception.getClass())) {
        	 // 白名单需要重连
            retry = true;
        } else if (!sent) {
        	// 还没完全发送
            retry = true;
        }

        if(retry) {
        	// 重新发送所有的idempotent请求
            HttpUriRequest currentReq = (HttpUriRequest) context.getAttribute( ExecutionContext.HTTP_REQUEST );
            String requestType = currentReq.getMethod();
            retry = !requestType.equals("POST");
        }

        if(retry) {
            SystemClock.sleep(RETRY_SLEEP_TIME_MILLIS);
        } else {
            exception.printStackTrace();
        }

        return retry;
    }
}