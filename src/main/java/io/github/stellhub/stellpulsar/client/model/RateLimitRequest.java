package io.github.stellhub.stellpulsar.client.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RateLimitRequest(
        String requestId,
        String applicationCode,
        String targetService,
        String resource,
        String method,
        String tenantId,
        String userId,
        String quotaKey,
        long cost,
        Map<String, String> attributes) {

    public RateLimitRequest {
        requestId = defaultText(requestId, UUID.randomUUID().toString());
        applicationCode = requireText(applicationCode, "applicationCode");
        targetService = defaultText(targetService, applicationCode);
        resource = defaultText(resource, "");
        method = defaultText(method, "");
        tenantId = defaultText(tenantId, "");
        userId = defaultText(userId, "");
        quotaKey = defaultText(quotaKey, "");
        cost = Math.max(0, cost);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * 创建请求构造器。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回标准字段和扩展字段合并后的属性视图。
     */
    public Map<String, String> attributeView() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>(attributes);
        putIfPresent(values, "applicationCode", applicationCode);
        putIfPresent(values, "targetService", targetService);
        putIfPresent(values, "resource", resource);
        putIfPresent(values, "method", method);
        putIfPresent(values, "tenantId", tenantId);
        putIfPresent(values, "userId", userId);
        putIfPresent(values, "quotaKey", quotaKey);
        return Map.copyOf(values);
    }

    /**
     * 按名称读取标准字段或扩展属性。
     */
    public String attribute(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return attributeView().getOrDefault(name, "");
    }

    public static final class Builder {

        private String requestId;
        private String applicationCode;
        private String targetService;
        private String resource;
        private String method;
        private String tenantId;
        private String userId;
        private String quotaKey;
        private long cost;
        private final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * 设置请求 ID。
         */
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        /**
         * 设置应用编码。
         */
        public Builder applicationCode(String applicationCode) {
            this.applicationCode = applicationCode;
            return this;
        }

        /**
         * 设置目标服务。
         */
        public Builder targetService(String targetService) {
            this.targetService = targetService;
            return this;
        }

        /**
         * 设置资源标识。
         */
        public Builder resource(String resource) {
            this.resource = resource;
            return this;
        }

        /**
         * 设置请求方法。
         */
        public Builder method(String method) {
            this.method = method;
            return this;
        }

        /**
         * 设置租户标识。
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * 设置用户标识。
         */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * 设置显式配额 key。
         */
        public Builder quotaKey(String quotaKey) {
            this.quotaKey = quotaKey;
            return this;
        }

        /**
         * 设置本次请求消耗配额。
         */
        public Builder cost(long cost) {
            this.cost = cost;
            return this;
        }

        /**
         * 批量设置扩展属性。
         */
        public Builder attributes(Map<String, String> attributes) {
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
         * 构建限流请求。
         */
        public RateLimitRequest build() {
            return new RateLimitRequest(
                    requestId,
                    applicationCode,
                    targetService,
                    resource,
                    method,
                    tenantId,
                    userId,
                    quotaKey,
                    cost,
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
        return value == null || value.isBlank() ? Objects.requireNonNull(defaultValue) : value;
    }

    private static void putIfPresent(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value);
        }
    }
}
