package com.king.http;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import com.king.http.cache.CachedHttpResponse.ResponseData;
import com.king.http.cache.HttpResponseCache;

public class AsyncHttpRequest implements Runnable {
	
	private final AbstractHttpClient client;
	private final HttpContext context;
	private final HttpUriRequest request;
	private final HttpResponseHandler responseHandler;
	private boolean isBinaryRequest;
	private int executionCount;
	private HttpResponseCache responseCache;

	public AsyncHttpRequest(AbstractHttpClient client, HttpContext context,
			HttpUriRequest request, HttpResponseHandler responseHandler,HttpResponseCache responseCache) {
		this.client = client;
		this.context = context;
		this.request = request;
		this.responseHandler = responseHandler;
		this.responseCache = responseCache;
		if (responseHandler instanceof BinaryHttpResponseHandler) {
			this.isBinaryRequest = true;
		}
	}
	
	@Override
	public void run() {
		try {
			if (responseHandler != null) {
				responseHandler.sendStartMessage();
			}

			makeRequestWithRetries();

			if (responseHandler != null) {
				responseHandler.sendFinishMessage();
			}
		} catch (IOException e) {
			if (responseHandler != null) {
				responseHandler.sendFinishMessage();
				if (this.isBinaryRequest) {
					responseHandler.sendFailureMessage(e, (byte[]) null);
				} else {
					responseHandler.sendFailureMessage(e, (String) null);
				}
			}
		}
	}
	
	private void makeRequest() throws IOException {
		if (!Thread.currentThread().isInterrupted()) {
			
			//TODO：  如果 用户new了多个  KingHttpClient.cache缓存到disk时候,无法重新
			// 获取cache
		   	if(responseCache != null && responseCache.containsKey(request.getURI().toString())){
		   		responseHandler.sendResponseCache(responseCache,request.getURI().toString());
	    		return;
	    	}
			
			HttpResponse response = client.execute(request, context);
			if (!Thread.currentThread().isInterrupted()) {
				if (responseHandler != null) {
					//放入cache
			        int status = response.getStatusLine().getStatusCode();
		            HttpEntity temp = response.getEntity();
					if(responseCache != null && temp != null){
						HttpEntity entity = new BufferedHttpEntity(temp);
						byte[] bb = EntityUtils.toByteArray(entity);
			            ResponseData responseData = new ResponseData(status, bb);
			            responseCache.put(request.getURI().toString(), responseData);
					}
					//cache end
					responseHandler.sendResponseMessage(response);
				}
			} else {
				// TODO: 是否要抛出InterruptedException?
				// 在response接收到之前,请求被取消 
			}
		}
	}

	private void makeRequestWithRetries() throws ConnectException {
		boolean retry = true;
		IOException cause = null;
		HttpRequestRetryHandler retryHandler = client.getHttpRequestRetryHandler();
		while (retry) {
			try {
				makeRequest();
				return;
			} catch (UnknownHostException e) {
				if (responseHandler != null) {
					responseHandler.sendFailureMessage(e, "can't resolve host");
				}
				return;
			} catch (IOException e) {
				cause = e;
				retry = retryHandler.retryRequest(cause, ++executionCount,
						context);
			} catch (NullPointerException e) {
				// there's a bug in HttpClient 4.0.x that on some occasions
				// causes
				// DefaultRequestExecutor to throw an NPE, see
				// http://code.google.com/p/android/issues/detail?id=5255
				cause = new IOException("NPE in HttpClient" + e.getMessage());
				retry = retryHandler.retryRequest(cause, ++executionCount,
						context);
			}
		}

		// 不在重试,抛出异常
		ConnectException ex = new ConnectException();
		ex.initCause(cause);
		throw ex;
	}
}
