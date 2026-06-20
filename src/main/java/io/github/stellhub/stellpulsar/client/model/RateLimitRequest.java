package io.github.stellhub.stellpulsar.client.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RateLimitRequest(
        String requestId,
        String applicationCode,
        String targetService,
        String resource,
        String method,
        String endpoint,
        String tenantId,
        String userId,
        String caller,
        String apiKey,
        String remoteIp,
        String modelRequest,
        long modelTokens,
        long modelCost,
        String quotaKey,
        long cost,
        String unit,
        Map<String, String> headers,
        Map<String, String> grpcMetadata,
        Map<String, String> attributes) {

    public RateLimitRequest {
        requestId = defaultText(requestId, UUID.randomUUID().toString());
        applicationCode = requireText(applicationCode, "applicationCode");
        targetService = defaultText(targetService, applicationCode);
        resource = defaultText(resource, "");
        method = defaultText(method, "");
        endpoint = defaultText(endpoint, resource);
        tenantId = defaultText(tenantId, "");
        userId = defaultText(userId, "");
        caller = defaultText(caller, "");
        apiKey = defaultText(apiKey, "");
        remoteIp = defaultText(remoteIp, "");
        modelRequest = defaultText(modelRequest, "");
        modelTokens = Math.max(0, modelTokens);
        modelCost = Math.max(0, modelCost);
        quotaKey = defaultText(quotaKey, "");
        cost = Math.max(0, cost);
        unit = defaultText(unit, "");
        headers = copyHeaders(headers);
        grpcMetadata = copyCaseInsensitive(grpcMetadata);
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
        putIfPresent(values, "path", resource);
        putIfPresent(values, "method", method);
        putIfPresent(values, "endpoint", endpoint);
        putIfPresent(values, "tenantId", tenantId);
        putIfPresent(values, "userId", userId);
        putIfPresent(values, "caller", caller);
        putIfPresent(values, "apiKey", apiKey);
        putIfPresent(values, "remoteIp", remoteIp);
        putIfPresent(values, "modelRequest", modelRequest);
        putIfPresent(values, "modelTokens", positiveLong(modelTokens));
        putIfPresent(values, "modelToken", positiveLong(modelTokens));
        putIfPresent(values, "modelCost", positiveLong(modelCost));
        putIfPresent(values, "quotaKey", quotaKey);
        putIfPresent(values, "unit", unit);
        headers.forEach((key, value) -> putIfPresent(values, "header." + key, value));
        grpcMetadata.forEach((key, value) -> putIfPresent(values, "grpcMetadata." + key, value));
        return Map.copyOf(values);
    }

    /**
     * 按名称读取标准字段或扩展属性。
     */
    public String attribute(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String key = name.trim();
        String lookupKey = key.toLowerCase(Locale.ROOT);
        if (lookupKey.startsWith("header.")) {
            return header(key.substring("header.".length()));
        }
        if (lookupKey.startsWith("headers.")) {
            return header(key.substring("headers.".length()));
        }
        if (lookupKey.startsWith("grpcmetadata.")) {
            return metadata(key.substring("grpcMetadata.".length()));
        }
        if (lookupKey.startsWith("grpc_metadata.")) {
            return metadata(key.substring("grpc_metadata.".length()));
        }
        if (lookupKey.startsWith("metadata.")) {
            return metadata(key.substring("metadata.".length()));
        }
        String value = standardAttribute(key);
        if (!value.isBlank()) {
            return value;
        }
        return attributes.getOrDefault(key, "");
    }

    /**
     * 按名称读取 HTTP header。
     */
    public String header(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return headers.getOrDefault(normalizeLookupKey(name), "");
    }

    /**
     * 按名称读取 gRPC metadata。
     */
    public String metadata(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return grpcMetadata.getOrDefault(normalizeLookupKey(name), "");
    }

    public static final class Builder {

        private String requestId;
        private String applicationCode;
        private String targetService;
        private String resource;
        private String method;
        private String endpoint;
        private String tenantId;
        private String userId;
        private String caller;
        private String apiKey;
        private String remoteIp;
        private String modelRequest;
        private long modelTokens;
        private long modelCost;
        private String quotaKey;
        private long cost;
        private String unit;
        private final LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> grpcMetadata = new LinkedHashMap<>();
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
         * 设置端点标识。
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
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
         * 设置调用方标识。
         */
        public Builder caller(String caller) {
            this.caller = caller;
            return this;
        }

        /**
         * 设置 API Key 标识。
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * 设置远端 IP。
         */
        public Builder remoteIp(String remoteIp) {
            this.remoteIp = remoteIp;
            return this;
        }

        /**
         * 设置模型请求标识。
         */
        public Builder modelRequest(String modelRequest) {
            this.modelRequest = modelRequest;
            return this;
        }

        /**
         * 设置模型 token 消耗。
         */
        public Builder modelTokens(long modelTokens) {
            this.modelTokens = modelTokens;
            return this;
        }

        /**
         * 设置模型成本消耗。
         */
        public Builder modelCost(long modelCost) {
            this.modelCost = modelCost;
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
         * 设置配额消耗单位。
         */
        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        /**
         * 批量设置 HTTP headers。
         */
        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                headers.forEach(this::header);
            }
            return this;
        }

        /**
         * 设置单个 HTTP header。
         */
        public Builder header(String key, String value) {
            putIfPresent(headers, normalizeLookupKey(key), value);
            return this;
        }

        /**
         * 批量设置 gRPC metadata。
         */
        public Builder grpcMetadata(Map<String, String> grpcMetadata) {
            if (grpcMetadata != null) {
                grpcMetadata.forEach(this::grpcMetadata);
            }
            return this;
        }

        /**
         * 设置单个 gRPC metadata。
         */
        public Builder grpcMetadata(String key, String value) {
            putIfPresent(grpcMetadata, normalizeLookupKey(key), value);
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
            putIfPresent(attributes, key, value);
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
                    endpoint,
                    tenantId,
                    userId,
                    caller,
                    apiKey,
                    remoteIp,
                    modelRequest,
                    modelTokens,
                    modelCost,
                    quotaKey,
                    cost,
                    unit,
                    headers,
                    grpcMetadata,
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

    private static Map<String, String> copyHeaders(Map<String, String> values) {
        return copyCaseInsensitive(values);
    }

    private static Map<String, String> copyCaseInsensitive(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> copied = new LinkedHashMap<>();
        values.forEach((key, value) -> putIfPresent(copied, normalizeLookupKey(key), value));
        return Map.copyOf(copied);
    }

    private static String normalizeLookupKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String standardAttribute(String key) {
        return switch (normalizeEnum(key)) {
            case "APPLICATION_CODE", "APPLICATIONCODE" -> applicationCode;
            case "TARGET_SERVICE", "TARGETSERVICE", "SERVICE" -> targetService;
            case "RESOURCE", "PATH", "HTTP_PATH" -> resource;
            case "METHOD", "GRPC_METHOD" -> method;
            case "ENDPOINT" -> endpoint;
            case "TENANT", "TENANT_ID", "TENANTID" -> tenantId;
            case "USER", "USER_ID", "USERID" -> userId;
            case "CALLER" -> caller;
            case "API_KEY", "APIKEY" -> apiKey;
            case "REMOTE_IP", "REMOTEIP", "IP" -> remoteIp;
            case "MODEL_REQUEST", "MODELREQUEST" -> modelRequest;
            case "MODEL_TOKEN", "MODEL_TOKENS", "MODELTOKEN", "MODELTOKENS" -> positiveLong(modelTokens);
            case "MODEL_COST", "MODELCOST" -> positiveLong(modelCost);
            case "QUOTA_KEY", "QUOTAKEY" -> quotaKey;
            case "UNIT" -> unit;
            default -> "";
        };
    }

    private static String normalizeEnum(String value) {
        return value == null ? "" : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private static String positiveLong(long value) {
        return value > 0 ? Long.toString(value) : "";
    }

    private static void putIfPresent(Map<String, String> values, String key, String value) {
        if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
            values.put(key, value);
        }
    }
}
