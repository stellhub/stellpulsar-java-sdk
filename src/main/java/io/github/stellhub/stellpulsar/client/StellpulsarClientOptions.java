package io.github.stellhub.stellpulsar.client;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public final class StellpulsarClientOptions {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final URI endpoint;
    private final String apiKey;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    private StellpulsarClientOptions(Builder builder) {
        this.endpoint = Objects.requireNonNull(builder.endpoint, "endpoint must not be null");
        this.apiKey = builder.apiKey;
        this.connectTimeout = builder.connectTimeout;
        this.requestTimeout = builder.requestTimeout;
    }

    /**
     * 创建客户端配置构建器。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回 StellPulsar 服务端地址。
     */
    public URI endpoint() {
        return endpoint;
    }

    /**
     * 返回 API Key。
     */
    public String apiKey() {
        return apiKey;
    }

    /**
     * 返回连接超时时间。
     */
    public Duration connectTimeout() {
        return connectTimeout;
    }

    /**
     * 返回请求超时时间。
     */
    public Duration requestTimeout() {
        return requestTimeout;
    }

    public static final class Builder {

        private URI endpoint;
        private String apiKey;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;

        private Builder() {
        }

        /**
         * 设置 StellPulsar 服务端地址。
         */
        public Builder endpoint(URI endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * 设置 API Key。
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * 设置连接超时时间。
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
            return this;
        }

        /**
         * 设置请求超时时间。
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
            return this;
        }

        /**
         * 构建客户端配置。
         */
        public StellpulsarClientOptions build() {
            return new StellpulsarClientOptions(this);
        }
    }
}
