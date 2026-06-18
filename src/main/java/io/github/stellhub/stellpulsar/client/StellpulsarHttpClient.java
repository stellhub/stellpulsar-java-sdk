package io.github.stellhub.stellpulsar.client;

import io.github.stellhub.stellpulsar.client.internal.Jsons;
import io.github.stellhub.stellpulsar.client.model.ApiResponse;
import io.github.stellhub.stellpulsar.client.model.LimitCheckRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

public final class StellpulsarHttpClient implements StellpulsarClient {

    private static final String API_KEY_HEADER = "X-Stellpulsar-Api-Key";

    private final StellpulsarClientOptions options;
    private final HttpClient httpClient;

    public StellpulsarHttpClient(StellpulsarClientOptions options) {
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(options.connectTimeout())
                .build();
    }

    /**
     * 请求限流决策。
     */
    @Override
    public ApiResponse check(LimitCheckRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        HttpRequest httpRequest = requestBuilder("/api/stellpulsar/v1/limits/check")
                .POST(HttpRequest.BodyPublishers.ofString(Jsons.limitCheckRequest(request)))
                .header("Content-Type", "application/json")
                .build();
        return send(httpRequest);
    }

    /**
     * 关闭客户端资源。
     */
    @Override
    public void close() {
        // java.net.http.HttpClient does not require explicit shutdown.
    }

    private HttpRequest.Builder requestBuilder(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
                .timeout(options.requestTimeout())
                .header("Accept", "application/json")
                .header("User-Agent", "stellpulsar-java-sdk");
        if (options.apiKey() != null && !options.apiKey().isBlank()) {
            builder.header(API_KEY_HEADER, options.apiKey());
        }
        return builder;
    }

    private ApiResponse send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new ApiResponse(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new StellpulsarClientException("failed to call StellPulsar service", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StellpulsarClientException("StellPulsar request was interrupted", e);
        }
    }

    private URI resolve(String path) {
        String base = options.endpoint().toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + path);
    }
}
