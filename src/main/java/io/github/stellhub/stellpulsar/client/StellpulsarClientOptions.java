package io.github.stellhub.stellpulsar.client;

import io.github.stellhub.stellpulsar.client.event.StellpulsarEventListener;
import io.github.stellhub.stellpulsar.client.model.FailPolicy;
import io.github.stellhub.stellpulsar.client.quota.QuotaGateway;
import io.github.stellhub.stellpulsar.client.rule.DistributedRateLimitRuleProvider;
import io.github.stellhub.stellpulsar.client.topology.TopologyManager;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class StellpulsarClientOptions {

    private final String applicationCode;
    private final String clientId;
    private final String namespace;
    private final String serviceName;
    private final int maxAcquireAttempts;
    private final Duration retryDelay;
    private final FailPolicy defaultFailPolicy;
    private final DistributedRateLimitRuleProvider ruleProvider;
    private final TopologyManager topologyManager;
    private final QuotaGateway quotaGateway;
    private final StellpulsarEventListener eventListener;
    private final Map<String, String> labels;

    private StellpulsarClientOptions(Builder builder) {
        this.applicationCode = requireText(builder.applicationCode, "applicationCode");
        this.clientId = defaultText(builder.clientId, "stellpulsar-java-" + UUID.randomUUID());
        this.namespace = defaultText(builder.namespace, "default");
        this.serviceName = defaultText(builder.serviceName, "stellpulsar-service");
        this.maxAcquireAttempts = Math.max(1, builder.maxAcquireAttempts);
        this.retryDelay = Objects.requireNonNull(builder.retryDelay, "retryDelay must not be null");
        this.defaultFailPolicy = Objects.requireNonNull(builder.defaultFailPolicy, "defaultFailPolicy must not be null");
        this.ruleProvider = Objects.requireNonNull(builder.ruleProvider, "ruleProvider must not be null");
        this.topologyManager = Objects.requireNonNull(builder.topologyManager, "topologyManager must not be null");
        this.quotaGateway = Objects.requireNonNull(builder.quotaGateway, "quotaGateway must not be null");
        this.eventListener = builder.eventListener == null
                ? StellpulsarEventListener.NOOP
                : builder.eventListener;
        this.labels = Map.copyOf(builder.labels);
    }

    /**
     * 创建客户端配置构造器。
     */
    public static Builder builder() {
        return new Builder();
    }

    public String applicationCode() {
        return applicationCode;
    }

    public String clientId() {
        return clientId;
    }

    public String namespace() {
        return namespace;
    }

    public String serviceName() {
        return serviceName;
    }

    public int maxAcquireAttempts() {
        return maxAcquireAttempts;
    }

    public Duration retryDelay() {
        return retryDelay;
    }

    public FailPolicy defaultFailPolicy() {
        return defaultFailPolicy;
    }

    public DistributedRateLimitRuleProvider ruleProvider() {
        return ruleProvider;
    }

    public TopologyManager topologyManager() {
        return topologyManager;
    }

    public QuotaGateway quotaGateway() {
        return quotaGateway;
    }

    public StellpulsarEventListener eventListener() {
        return eventListener;
    }

    public Map<String, String> labels() {
        return labels;
    }

    public static final class Builder {

        private String applicationCode;
        private String clientId;
        private String namespace = "default";
        private String serviceName = "stellpulsar-service";
        private int maxAcquireAttempts = 3;
        private Duration retryDelay = Duration.ofMillis(50);
        private FailPolicy defaultFailPolicy = FailPolicy.FAIL_OPEN;
        private DistributedRateLimitRuleProvider ruleProvider;
        private TopologyManager topologyManager;
        private QuotaGateway quotaGateway;
        private StellpulsarEventListener eventListener = StellpulsarEventListener.NOOP;
        private final LinkedHashMap<String, String> labels = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * 设置应用编码。
         */
        public Builder applicationCode(String applicationCode) {
            this.applicationCode = applicationCode;
            return this;
        }

        /**
         * 设置客户端 ID。
         */
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /**
         * 设置命名空间。
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * 设置 StellPulsar 服务名。
         */
        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        /**
         * 设置单条规则最大请求尝试次数。
         */
        public Builder maxAcquireAttempts(int maxAcquireAttempts) {
            this.maxAcquireAttempts = maxAcquireAttempts;
            return this;
        }

        /**
         * 设置默认重试等待时间。
         */
        public Builder retryDelay(Duration retryDelay) {
            this.retryDelay = Objects.requireNonNull(retryDelay, "retryDelay must not be null");
            return this;
        }

        /**
         * 设置默认失败降级策略。
         */
        public Builder defaultFailPolicy(FailPolicy defaultFailPolicy) {
            this.defaultFailPolicy = Objects.requireNonNull(defaultFailPolicy, "defaultFailPolicy must not be null");
            return this;
        }

        /**
         * 设置规则提供器。
         */
        public Builder ruleProvider(DistributedRateLimitRuleProvider ruleProvider) {
            this.ruleProvider = ruleProvider;
            return this;
        }

        /**
         * 设置 topology 管理器。
         */
        public Builder topologyManager(TopologyManager topologyManager) {
            this.topologyManager = topologyManager;
            return this;
        }

        /**
         * 设置配额网关。
         */
        public Builder quotaGateway(QuotaGateway quotaGateway) {
            this.quotaGateway = quotaGateway;
            return this;
        }

        /**
         * 设置事件监听器。
         */
        public Builder eventListener(StellpulsarEventListener eventListener) {
            this.eventListener = eventListener == null ? StellpulsarEventListener.NOOP : eventListener;
            return this;
        }

        /**
         * 批量设置客户端标签。
         */
        public Builder labels(Map<String, String> labels) {
            if (labels != null) {
                labels.forEach(this::label);
            }
            return this;
        }

        /**
         * 设置单个客户端标签。
         */
        public Builder label(String key, String value) {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                labels.put(key, value);
            }
            return this;
        }

        /**
         * 构建客户端配置。
         */
        public StellpulsarClientOptions build() {
            return new StellpulsarClientOptions(this);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
