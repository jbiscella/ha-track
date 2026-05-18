package org.hatrack.frauholle.eodhd.internal;

import org.hatrack.frauholle.eodhd.HttpExecutor;
import org.hatrack.frauholle.eodhd.HttpResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** JDK-only default {@link HttpExecutor}, backed by {@code java.net.http.HttpClient}. */
public final class JdkHttpExecutor implements HttpExecutor {

    private final HttpClient client;

    public JdkHttpExecutor(Duration connectTimeout) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    @Override
    public HttpResult get(String url, Map<String, String> headers, Duration timeout) throws IOException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET().timeout(timeout);
        headers.forEach(request::header);
        try {
            HttpResponse<String> response = client.send(request.build(),
                    HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }
}
