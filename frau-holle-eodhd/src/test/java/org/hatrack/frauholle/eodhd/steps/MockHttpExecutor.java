package org.hatrack.frauholle.eodhd.steps;

import org.hatrack.frauholle.eodhd.HttpExecutor;
import org.hatrack.frauholle.eodhd.HttpResult;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Test double for {@link HttpExecutor}: canned responses, no real network. */
final class MockHttpExecutor implements HttpExecutor {

    private int statusCode = 200;
    private String body = "[]";
    private IOException failure;
    private final List<String> requestedUrls = new ArrayList<>();
    private Map<String, String> lastHeaders;

    void respondWith(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
        this.failure = null;
    }

    void failWith(IOException failure) {
        this.failure = failure;
    }

    @Override
    public HttpResult get(String url, Map<String, String> headers, Duration timeout) throws IOException {
        requestedUrls.add(url);
        lastHeaders = headers;
        if (failure != null) {
            throw failure;
        }
        return new HttpResult(statusCode, body);
    }

    int callCount() {
        return requestedUrls.size();
    }

    String lastUrl() {
        return requestedUrls.isEmpty() ? null : requestedUrls.get(requestedUrls.size() - 1);
    }

    Map<String, String> lastHeaders() {
        return lastHeaders;
    }
}
