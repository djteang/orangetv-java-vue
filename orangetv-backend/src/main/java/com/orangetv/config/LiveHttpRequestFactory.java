package com.orangetv.config;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/** GET-only transport that closes live connections without draining an endless response body. */
public final class LiveHttpRequestFactory implements ClientHttpRequestFactory, DisposableBean {
    private final CloseableHttpClient client;

    public LiveHttpRequestFactory(CloseableHttpClient client) { this.client = client; }

    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod method) {
        if (method != HttpMethod.GET) throw new IllegalArgumentException("Live transport only supports GET");
        return new AbstractClientHttpRequest() {
            @Override public HttpMethod getMethod() { return method; }
            @Override public URI getURI() { return uri; }
            @Override protected OutputStream getBodyInternal(HttpHeaders headers) {
                throw new UnsupportedOperationException("GET request has no body");
            }
            @Override protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
                HttpUriRequestBase request = new HttpUriRequestBase(method.name(), uri);
                headers.forEach((name, values) -> values.forEach(value -> request.addHeader(name, value)));
                ClassicHttpResponse response = client.executeOpen(null, request, HttpClientContext.create());
                HttpHeaders responseHeaders = new HttpHeaders();
                for (var header : response.getHeaders()) responseHeaders.add(header.getName(), header.getValue());
                return new ClientHttpResponse() {
                    @Override public HttpStatusCode getStatusCode() { return HttpStatusCode.valueOf(response.getCode()); }
                    @Override public String getStatusText() { return response.getReasonPhrase(); }
                    @Override public HttpHeaders getHeaders() { return responseHeaders; }
                    @Override public InputStream getBody() throws IOException {
                        return response.getEntity() == null ? InputStream.nullInputStream() : response.getEntity().getContent();
                    }
                    @Override public void close() {
                        // RestTemplate also closes responses after probing a prefix or after a client disconnects.
                        // Apache's normal entity close drains the rest, which never ends for FLV/MPEG-TS.
                        request.cancel();
                        try { response.close(); } catch (IOException ignored) {}
                    }
                };
            }
        };
    }

    @Override public void destroy() throws IOException { client.close(); }
}

