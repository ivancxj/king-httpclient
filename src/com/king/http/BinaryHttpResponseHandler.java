package com.king.http;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.util.EntityUtils;

import com.king.http.cache.HttpResponseCache;
import com.king.http.cache.CachedHttpResponse.ResponseData;

import android.os.Message;

/**
 * KingHttpClient client = new KingHttpClient();
 * String[] allowedTypes = new String[] { "image/png" };
 * client.get("http://www.example.com/image.png", new BinaryHttpResponseHandler(allowedTypes) {
 *     &#064;Override
 *     public void onSuccess(byte[] imageData) {
 *         // Successfully got a response
 *     }
 *
 *     &#064;Override
 *     public void onFailure(Throwable e, byte[] imageData) {
 *         // Response failed :(
 *     }
 * });
 */
public class BinaryHttpResponseHandler extends HttpResponseHandler {
    // Allow images by default
    private static String[] mAllowedContentTypes = new String[] {
        "image/jpeg",
        "image/png"
    };
    public BinaryHttpResponseHandler() {
        super();
    }
    
    public BinaryHttpResponseHandler(String[] allowedContentTypes) {
        this();
        mAllowedContentTypes = allowedContentTypes;
    }
    
    public void onSuccess(byte[] binaryData) {}

    public void onFailure(Throwable error, byte[] binaryData) {
        // By default, call the deprecated onFailure(Throwable) for compatibility
        onFailure(error);
    }


    //
    // Pre-processing of messages (executes in background threadpool thread)
    //

    protected void sendSuccessMessage(byte[] responseBody) {
        sendMessage(obtainMessage(SUCCESS_MESSAGE, responseBody));
    }

    protected void sendFailureMessage(Throwable e, byte[] responseBody) {
        sendMessage(obtainMessage(FAILURE_MESSAGE, new Object[]{e, responseBody}));
    }

    //
    // Pre-processing of messages (in original calling thread, typically the UI thread)
    //

    protected void handleSuccessMessage(byte[] responseBody) {
        onSuccess(responseBody);
    }

    protected void handleFailureMessage(Throwable e, byte[] responseBody) {
        onFailure(e, responseBody);
    }

    // Methods which emulate android's Handler and Message methods
    protected void handleMessage(Message msg) {
        switch(msg.what) {
            case SUCCESS_MESSAGE:
                handleSuccessMessage((byte[])msg.obj);
                break;
            case FAILURE_MESSAGE:
                Object[] response = (Object[])msg.obj;
                handleFailureMessage((Throwable)response[0], (byte[])response[1]);
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }
    
    @Override
    void sendResponseCache(HttpResponseCache responseCache, String url){
    	ResponseData responseData = responseCache.get(url);
    	int status = responseData.getStatusCode();
    	byte[] responseBody = responseData.getResponseBody();
        if(status >= 300) {
        	//TODO 错误处理待 更详细
            sendFailureMessage(new HttpResponseException(status, "TODO"), responseBody);
        } else {
        	sendSuccessMessage(responseBody);
        }
    }

    @Override
    void sendResponseMessage(HttpResponse response) {
        StatusLine status = response.getStatusLine();
        Header[] contentTypeHeaders = response.getHeaders("Content-Type");
        byte[] responseBody = null;
        if(contentTypeHeaders.length != 1) {
            //malformed/ambiguous HTTP Header, ABORT!
            sendFailureMessage(new HttpResponseException(status.getStatusCode(), "None, or more than one, Content-Type Header found!"), responseBody);
            return;
        }
        Header contentTypeHeader = contentTypeHeaders[0];
        boolean foundAllowedContentType = false;
        for(String anAllowedContentType : mAllowedContentTypes) {
            if(anAllowedContentType.equals(contentTypeHeader.getValue())) {
                foundAllowedContentType = true;
            }
        }
        if(!foundAllowedContentType) {
            //Content-Type not in allowed list, ABORT!
            sendFailureMessage(new HttpResponseException(status.getStatusCode(), "Content-Type not allowed!"), responseBody);
            return;
        }
        try {
            HttpEntity entity = null;
            HttpEntity temp = response.getEntity();
            if(temp != null) {
                entity = new BufferedHttpEntity(temp);
            }
            responseBody = EntityUtils.toByteArray(entity);
        } catch(IOException e) {
            sendFailureMessage(e, (byte[]) null);
        }

        if(status.getStatusCode() >= 300) {
            sendFailureMessage(new HttpResponseException(status.getStatusCode(), status.getReasonPhrase()), responseBody);
        } else {
            sendSuccessMessage(responseBody);
        }
    }
}