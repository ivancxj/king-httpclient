package com.king.http.cache;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpResponse;

public interface IHttpResponse {

    public HttpResponse unwrap();

    public InputStream getResponseBody() throws IOException;

    public byte[] getResponseBodyAsBytes() throws IOException;

    public String getResponseBodyAsString() throws IOException;

    public int getStatusCode();

    public String getHeader(String header);
}
