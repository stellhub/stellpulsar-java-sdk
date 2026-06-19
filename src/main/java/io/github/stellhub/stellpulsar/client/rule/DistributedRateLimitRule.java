package io.github.stellhub.stellpulsar.client.rule;

import io.github.stellhub.stellpulsar.client.model.FailPolicy;
import io.github.stellhub.stellpulsar.client.model.RateLimitRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

public record DistributedRateLimitRule(
        String applicationCode,
        String ruleId,
        String ruleName,
        String revision,
        String checksum,
        String schemaVersion,
        String algorithm,
        long quota,
        long windowSeconds,
        long burst,
        List<String> dimensions,
        long cost,
        FailPolicy failPolicy,
        Map<String, String> attributes) {

    public DistributedRateLimitRule {
        applicationCode = requireText(applicationCode, "applicationCode");
        ruleId = requireText(ruleId, "ruleId");
        ruleName = defaultText(ruleName, ruleId);
        revision = defaultText(revision, "");
        checksum = defaultText(checksum, "");
        schemaVersion = defaultText(schemaVersion, "v1");
        algorithm = defaultText(algorithm, "");
        dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
        cost = cost <= 0 ? 1 : cost;
        failPolicy = failPolicy == null ? FailPolicy.FAIL_OPEN : failPolicy;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * 创建分布式规则构造器。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 判断规则是否具备远端扣减需要的 digest。
     */
    public boolean hasDigest() {
        return !revision.isBlank() && !checksum.isBlank();
    }

    /**
     * 计算当前请求对应的配额 key。
     */
    public String resolveQuotaKey(RateLimitRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!request.quotaKey().isBlank()) {
            return request.quotaKey();
        }
        List<String> resolvedDimensions = dimensions.isEmpty() ? List.of("tenantId", "resource", "method") : dimensions;
        StringJoiner joiner = new StringJoiner(":");
        for (String dimension : resolvedDimensions) {
            String value = request.attribute(dimension);
            if (!value.isBlank()) {
                joiner.add(dimension + "=" + value);
            }
        }
        String value = joiner.toString();
        if (!value.isBlank()) {
            return value;
        }
        return request.targetService() + ":" + request.resource() + ":" + request.method();
    }

    public static final class Builder {

        private String applicationCode;
        private String ruleId;
        private String ruleName;
        private String revision;
        private String checksum;
        private String schemaVersion = "v1";
        private String algorithm;
        private long quota;
        private long windowSeconds;
        private long burst;
        private final List<String> dimensions = new ArrayList<>();
        private long cost = 1;
        private FailPolicy failPolicy = FailPolicy.FAIL_OPEN;
        private final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();

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
         * 设置规则 ID。
         */
        public Builder ruleId(String ruleId) {
            this.ruleId = ruleId;
            return this;
        }

        /**
         * 设置规则名称。
         */
        public Builder ruleName(String ruleName) {
            this.ruleName = ruleName;
            return this;
        }

        /**
         * 设置规则版本。
         */
        public Builder revision(String revision) {
            this.revision = revision;
            return this;
        }

        /**
         * 设置规则 checksum。
         */
        public Builder checksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        /**
         * 设置 schema 版本。
         */
        public Builder schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        /**
         * 设置限流算法。
         */
        public Builder algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        /**
         * 设置窗口配额。
         */
        public Builder quota(long quota) {
            this.quota = quota;
            return this;
        }

        /**
         * 设置窗口秒数。
         */
        public Builder windowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
            return this;
        }

        /**
         * 设置突发配额。
         */
        public Builder burst(long burst) {
            this.burst = burst;
            return this;
        }

        /**
         * 设置配额 key 维度。
         */
        public Builder dimensions(List<String> dimensions) {
            this.dimensions.clear();
            if (dimensions != null) {
                dimensions.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .forEach(this.dimensions::add);
            }
            return this;
        }

        /**
         * 设置默认消耗配额。
         */
        public Builder cost(long cost) {
            this.cost = cost;
            return this;
        }

        /**
         * 设置失败降级策略。
         */
        public Builder failPolicy(FailPolicy failPolicy) {
            this.failPolicy = failPolicy;
            return this;
        }

        /**
         * 设置扩展属性。
         */
        public Builder attributes(Map<String, String> attributes) {
            this.attributes.clear();
            if (attributes != null) {
                attributes.forEach(this::attribute);
            }
            return this;
        }

        /**
         * 设置单个扩展属性。
         */
        public Builder attribute(String key, String value) {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                attributes.put(key, value);
            }
            return this;
        }

        /**
         * 构建分布式限流规则。
         */
        public DistributedRateLimitRule build() {
            return new DistributedRateLimitRule(
                    applicationCode,
                    ruleId,
                    ruleName,
                    revision,
                    checksum,
                    schemaVersion,
                    algorithm,
                    quota,
                    windowSeconds,
                    burst,
                    dimensions,
                    cost,
                    failPolicy,
                    attributes);
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
