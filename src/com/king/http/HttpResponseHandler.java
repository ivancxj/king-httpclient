package com.king.http;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.util.EntityUtils;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.king.http.cache.HttpResponseCache;
import com.king.http.cache.CachedHttpResponse.ResponseData;

public class HttpResponseHandler {
	
    protected static final int SUCCESS_MESSAGE = 0;
    protected static final int FAILURE_MESSAGE = 1;
    protected static final int START_MESSAGE = 2;
    protected static final int FINISH_MESSAGE = 3;
    
    private Handler handler;
    
    public HttpResponseHandler() {
        if(Looper.myLooper() != null) {
            handler = new Handler(){
            	@Override
                public void handleMessage(Message msg){
                	HttpResponseHandler.this.handleMessage(msg);
                }
            };
        }
    }
    
    public void onStart() {}

    public void onFinish() {}

    public void onSuccess(String content) {}
    
    public void onFailure(Throwable error) {}

    public void onFailure(Throwable error, String content) 
    {
    	onFailure(error);
    }


    protected void sendSuccessMessage(String responseBody) {
        sendMessage(obtainMessage(SUCCESS_MESSAGE, responseBody));
    }

    protected void sendFailureMessage(Throwable e, String responseBody) {
        sendMessage(obtainMessage(FAILURE_MESSAGE, new Object[]{e, responseBody}));
    }
    
    protected void sendFailureMessage(Throwable e, byte[] responseBody) {
        sendMessage(obtainMessage(FAILURE_MESSAGE, new Object[]{e, responseBody}));
    }

    protected void sendStartMessage() {
        sendMessage(obtainMessage(START_MESSAGE, null));
    }

    protected void sendFinishMessage() {
        sendMessage(obtainMessage(FINISH_MESSAGE, null));
    }

    protected void handleSuccessMessage(String responseBody) {
        onSuccess(responseBody);
    }

    protected void handleFailureMessage(Throwable e, String responseBody) {
        onFailure(e, responseBody);
    }

    // 模拟Handler
    protected void handleMessage(Message msg) {
        switch(msg.what) {
            case SUCCESS_MESSAGE:
                handleSuccessMessage((String)msg.obj);
                break;
            case FAILURE_MESSAGE:
                Object[] repsonse = (Object[])msg.obj;
                handleFailureMessage((Throwable)repsonse[0], (String)repsonse[1]);
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
        if(handler != null){
            handler.sendMessage(msg);
        } else {
            handleMessage(msg);
        }
    }

    protected Message obtainMessage(int responseMessage, Object response) {
        Message msg = null;
        if(handler != null){
            msg = this.handler.obtainMessage(responseMessage, response);
        }else{
            msg = new Message();
            msg.what = responseMessage;
            msg.obj = response;
        }
        return msg;
    }
    
    void sendResponseCache(HttpResponseCache responseCache, String url){
    	ResponseData responseData = responseCache.get(url);
    	int status = responseData.getStatusCode();
    	String responseBody = new String(responseData.getResponseBody());
        if(status >= 300) {
        	//TODO 错误处理待 更详细
            sendFailureMessage(new HttpResponseException(status, "TODO"), responseBody);
        } else {
        	sendSuccessMessage(responseBody);
        }
    }

    // 接收接口
    void sendResponseMessage(HttpResponse response) {
        StatusLine status = response.getStatusLine();
        String responseBody = null;
        try {
            HttpEntity entity = null;
            HttpEntity temp = response.getEntity();
            if(temp != null) {
                entity = new BufferedHttpEntity(temp);
                responseBody = EntityUtils.toString(entity, "UTF-8");
            }
        } catch(IOException e) {
            sendFailureMessage(e, (String) null);
        }

        if(status.getStatusCode() >= 300) {
            sendFailureMessage(new HttpResponseException(status.getStatusCode(), status.getReasonPhrase()), responseBody);
        } else {
            sendSuccessMessage(responseBody);
        }
    }

}
