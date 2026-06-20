package io.github.stellhub.stellpulsar.client.rule;

import io.github.stellhub.stellpulsar.client.model.FailPolicy;
import io.github.stellhub.stellpulsar.client.model.RateLimitRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public record DistributedRateLimitRule(
        String applicationCode,
        String ruleId,
        String ruleName,
        String revision,
        String checksum,
        String schemaVersion,
        String limitMode,
        String limitType,
        String limitAlgorithm,
        String trafficProtocol,
        String executionLocation,
        String coordinationMode,
        String algorithm,
        long quota,
        long windowSeconds,
        long burst,
        List<String> dimensions,
        long cost,
        FailPolicy failPolicy,
        Map<String, Object> requestMatcher,
        Map<String, Object> keyExtractor,
        Map<String, Object> quotaConfig,
        Map<String, Object> windowConfig,
        Map<String, Object> burstConfig,
        Map<String, Object> concurrencyConfig,
        Map<String, Object> hotspotConfig,
        Map<String, Object> customPolicy,
        Map<String, Object> modelLimitConfig,
        Map<String, Object> fallbackPolicy,
        Map<String, Object> responsePolicy,
        Map<String, Object> observabilityConfig,
        Map<String, Object> shadowConfig,
        Map<String, String> attributes) {

    public static final String LIMIT_MODE_QPS = "QPS";
    public static final String LIMIT_MODE_QUOTA = "QUOTA";
    public static final String LIMIT_MODE_HEADER = "HEADER";
    public static final String LIMIT_MODE_BANDWIDTH = "BANDWIDTH";
    public static final String LIMIT_MODE_MODEL = "MODEL";

    public static final String COORDINATION_MODE_GLOBAL_SYNC = "GLOBAL_SYNC";
    public static final String COORDINATION_MODE_GLOBAL_QUOTA = "GLOBAL_QUOTA";

    private static final Set<String> TRY_ACQUIRE_LIMIT_MODES = Set.of(
            LIMIT_MODE_QPS,
            LIMIT_MODE_QUOTA,
            LIMIT_MODE_HEADER,
            LIMIT_MODE_BANDWIDTH,
            LIMIT_MODE_MODEL);
    private static final Set<String> TRY_ACQUIRE_ALGORITHMS = Set.of(
            "TOKEN_BUCKET",
            "LEAKY_BUCKET",
            "FIXED_WINDOW",
            "SLIDING_WINDOW",
            "QUOTA_LEASE",
            "ADAPTIVE");
    private static final Set<String> KEY_EXTRACTOR_SOURCES = Set.of(
            "HEADER",
            "GRPC_METADATA",
            "HTTP_PATH",
            "GRPC_METHOD",
            "REMOTE_IP",
            "IP",
            "TENANT",
            "USER",
            "CALLER",
            "API_KEY",
            "RESOURCE",
            "ENDPOINT",
            "METHOD",
            "TOPIC",
            "MODEL_REQUEST",
            "MODEL_TOKEN",
            "MODEL_COST",
            "CUSTOM_KEY");
    private static final List<String> KEY_EXTRACTOR_COLLECTION_KEYS = List.of(
            "keys",
            "fields",
            "rules",
            "extractors",
            "keyExtractors",
            "key_extractors",
            "items");
    private static final Set<String> MATCH_OPERATOR_KEYS = Set.of(
            "value",
            "equals",
            "equal",
            "eq",
            "is",
            "notEquals",
            "notEqual",
            "ne",
            "in",
            "oneOf",
            "anyOf",
            "notIn",
            "contains",
            "prefix",
            "startsWith",
            "suffix",
            "endsWith",
            "regex",
            "pattern",
            "matches",
            "required",
            "exists",
            "ignoreCase",
            "caseInsensitive");

    public enum RequestMatchStatus {
        MATCHED,
        NOT_MATCHED,
        UNSUPPORTED
    }

    public DistributedRateLimitRule {
        applicationCode = requireText(applicationCode, "applicationCode");
        ruleId = requireText(ruleId, "ruleId");
        ruleName = defaultText(ruleName, ruleId);
        revision = defaultText(revision, "");
        checksum = defaultText(checksum, "");
        schemaVersion = defaultText(schemaVersion, "v1");
        limitMode = normalizeEnum(limitMode);
        limitType = normalizeEnum(limitType);
        limitAlgorithm = normalizeEnum(limitAlgorithm);
        trafficProtocol = normalizeEnum(trafficProtocol);
        executionLocation = normalizeEnum(executionLocation);
        coordinationMode = normalizeEnum(coordinationMode);
        algorithm = defaultText(algorithm, limitAlgorithm);
        dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
        cost = cost <= 0 ? 1 : cost;
        failPolicy = failPolicy == null ? FailPolicy.FAIL_OPEN : failPolicy;
        requestMatcher = copyObjectMap(requestMatcher);
        keyExtractor = copyObjectMap(keyExtractor);
        quotaConfig = copyObjectMap(quotaConfig);
        windowConfig = copyObjectMap(windowConfig);
        burstConfig = copyObjectMap(burstConfig);
        concurrencyConfig = copyObjectMap(concurrencyConfig);
        hotspotConfig = copyObjectMap(hotspotConfig);
        customPolicy = copyObjectMap(customPolicy);
        modelLimitConfig = copyObjectMap(modelLimitConfig);
        fallbackPolicy = copyObjectMap(fallbackPolicy);
        responsePolicy = copyObjectMap(responsePolicy);
        observabilityConfig = copyObjectMap(observabilityConfig);
        shadowConfig = copyObjectMap(shadowConfig);
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
     * 判断请求是否匹配当前规则。
     */
    public boolean matches(RateLimitRequest request) {
        return requestMatchStatus(request) == RequestMatchStatus.MATCHED;
    }

    /**
     * 判断请求与当前规则的匹配状态。
     */
    public RequestMatchStatus requestMatchStatus(RateLimitRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (requestMatcher.isEmpty()) {
            return RequestMatchStatus.MATCHED;
        }
        for (Map.Entry<String, Object> entry : requestMatcher.entrySet()) {
            RequestMatchStatus status = matchesMatcherEntry(request, entry.getKey(), entry.getValue());
            if (status != RequestMatchStatus.MATCHED) {
                return status;
            }
        }
        return RequestMatchStatus.MATCHED;
    }

    /**
     * 返回当前 tryAcquire 路径不支持的错误码。
     */
    public String unsupportedReason(RateLimitRequest request) {
        if (!TRY_ACQUIRE_LIMIT_MODES.contains(limitMode)) {
            return "UNSUPPORTED_LIMIT_MODE";
        }
        if (LIMIT_MODE_MODEL.equals(limitMode) && "MODEL_CONCURRENCY".equals(limitType)) {
            return "UNSUPPORTED_LIMIT_MODE";
        }
        if (limitAlgorithm.isBlank() || !TRY_ACQUIRE_ALGORITHMS.contains(limitAlgorithm)) {
            return "UNSUPPORTED_LIMIT_ALGORITHM";
        }
        if (hasUnsupportedKeyExtractorSchema()) {
            return "UNSUPPORTED_KEY_EXTRACTOR_SCHEMA";
        }
        for (Map<String, Object> extractor : keyExtractorEntries()) {
            String source = extractorSource(extractor);
            if (source.isBlank() || !KEY_EXTRACTOR_SOURCES.contains(source)) {
                return "UNSUPPORTED_KEY_EXTRACTOR_SOURCE";
            }
            if (booleanValue(extractor.get("required")) && extractorValue(request, extractor).isBlank()) {
                return "KEY_EXTRACTOR_VALUE_MISSING";
            }
        }
        return "";
    }

    /**
     * 计算当前请求对应的配额 key。
     */
    public String resolveQuotaKey(RateLimitRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!request.quotaKey().isBlank()) {
            return request.quotaKey();
        }
        String extracted = resolveKeyExtractor(request);
        if (!extracted.isBlank()) {
            return extracted;
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
        private String limitMode;
        private String limitType;
        private String limitAlgorithm;
        private String trafficProtocol;
        private String executionLocation;
        private String coordinationMode;
        private String algorithm;
        private long quota;
        private long windowSeconds;
        private long burst;
        private final List<String> dimensions = new ArrayList<>();
        private long cost = 1;
        private FailPolicy failPolicy = FailPolicy.FAIL_OPEN;
        private Map<String, Object> requestMatcher = Map.of();
        private Map<String, Object> keyExtractor = Map.of();
        private Map<String, Object> quotaConfig = Map.of();
        private Map<String, Object> windowConfig = Map.of();
        private Map<String, Object> burstConfig = Map.of();
        private Map<String, Object> concurrencyConfig = Map.of();
        private Map<String, Object> hotspotConfig = Map.of();
        private Map<String, Object> customPolicy = Map.of();
        private Map<String, Object> modelLimitConfig = Map.of();
        private Map<String, Object> fallbackPolicy = Map.of();
        private Map<String, Object> responsePolicy = Map.of();
        private Map<String, Object> observabilityConfig = Map.of();
        private Map<String, Object> shadowConfig = Map.of();
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
         * 设置限流模式。
         */
        public Builder limitMode(String limitMode) {
            this.limitMode = limitMode;
            return this;
        }

        /**
         * 设置限流对象类型。
         */
        public Builder limitType(String limitType) {
            this.limitType = limitType;
            return this;
        }

        /**
         * 设置限流算法。
         */
        public Builder limitAlgorithm(String limitAlgorithm) {
            this.limitAlgorithm = limitAlgorithm;
            this.algorithm = limitAlgorithm;
            return this;
        }

        /**
         * 设置流量协议。
         */
        public Builder trafficProtocol(String trafficProtocol) {
            this.trafficProtocol = trafficProtocol;
            return this;
        }

        /**
         * 设置执行位置。
         */
        public Builder executionLocation(String executionLocation) {
            this.executionLocation = executionLocation;
            return this;
        }

        /**
         * 设置协调模式。
         */
        public Builder coordinationMode(String coordinationMode) {
            this.coordinationMode = coordinationMode;
            return this;
        }

        /**
         * 设置限流算法。
         */
        public Builder algorithm(String algorithm) {
            this.algorithm = algorithm;
            this.limitAlgorithm = algorithm;
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
                        .filter(val -> val != null && !val.isBlank())
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
         * 设置请求匹配器。
         */
        public Builder requestMatcher(Map<String, Object> requestMatcher) {
            this.requestMatcher = copyObjectMap(requestMatcher);
            return this;
        }

        /**
         * 设置 key 提取器。
         */
        public Builder keyExtractor(Map<String, Object> keyExtractor) {
            this.keyExtractor = copyObjectMap(keyExtractor);
            return this;
        }

        /**
         * 设置配额配置。
         */
        public Builder quotaConfig(Map<String, Object> quotaConfig) {
            this.quotaConfig = copyObjectMap(quotaConfig);
            return this;
        }

        /**
         * 设置窗口配置。
         */
        public Builder windowConfig(Map<String, Object> windowConfig) {
            this.windowConfig = copyObjectMap(windowConfig);
            return this;
        }

        /**
         * 设置突发配置。
         */
        public Builder burstConfig(Map<String, Object> burstConfig) {
            this.burstConfig = copyObjectMap(burstConfig);
            return this;
        }

        /**
         * 设置并发配置。
         */
        public Builder concurrencyConfig(Map<String, Object> concurrencyConfig) {
            this.concurrencyConfig = copyObjectMap(concurrencyConfig);
            return this;
        }

        /**
         * 设置热点配置。
         */
        public Builder hotspotConfig(Map<String, Object> hotspotConfig) {
            this.hotspotConfig = copyObjectMap(hotspotConfig);
            return this;
        }

        /**
         * 设置自定义策略。
         */
        public Builder customPolicy(Map<String, Object> customPolicy) {
            this.customPolicy = copyObjectMap(customPolicy);
            return this;
        }

        /**
         * 设置模型限流配置。
         */
        public Builder modelLimitConfig(Map<String, Object> modelLimitConfig) {
            this.modelLimitConfig = copyObjectMap(modelLimitConfig);
            return this;
        }

        /**
         * 设置降级策略配置。
         */
        public Builder fallbackPolicy(Map<String, Object> fallbackPolicy) {
            this.fallbackPolicy = copyObjectMap(fallbackPolicy);
            return this;
        }

        /**
         * 设置响应策略。
         */
        public Builder responsePolicy(Map<String, Object> responsePolicy) {
            this.responsePolicy = copyObjectMap(responsePolicy);
            return this;
        }

        /**
         * 设置可观测性配置。
         */
        public Builder observabilityConfig(Map<String, Object> observabilityConfig) {
            this.observabilityConfig = copyObjectMap(observabilityConfig);
            return this;
        }

        /**
         * 设置影子模式配置。
         */
        public Builder shadowConfig(Map<String, Object> shadowConfig) {
            this.shadowConfig = copyObjectMap(shadowConfig);
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
                    limitMode,
                    limitType,
                    limitAlgorithm,
                    trafficProtocol,
                    executionLocation,
                    coordinationMode,
                    algorithm,
                    quota,
                    windowSeconds,
                    burst,
                    dimensions,
                    cost,
                    failPolicy,
                    requestMatcher,
                    keyExtractor,
                    quotaConfig,
                    windowConfig,
                    burstConfig,
                    concurrencyConfig,
                    hotspotConfig,
                    customPolicy,
                    modelLimitConfig,
                    fallbackPolicy,
                    responsePolicy,
                    observabilityConfig,
                    shadowConfig,
                    attributes);
        }
    }

    private RequestMatchStatus matchesMatcherEntry(RateLimitRequest request, String key, Object expected) {
        String normalizedKey = key == null ? "" : key.trim();
        if (normalizedKey.isBlank() || expected == null) {
            return RequestMatchStatus.MATCHED;
        }
        String normalized = normalizeEnum(normalizedKey);
        if (expected instanceof Map<?, ?> map && "NOT".equals(normalized)) {
            RequestMatchStatus status = matchesMatcherMap(request, map);
            if (status == RequestMatchStatus.UNSUPPORTED) {
                return status;
            }
            return status == RequestMatchStatus.MATCHED
                    ? RequestMatchStatus.NOT_MATCHED
                    : RequestMatchStatus.MATCHED;
        }
        if (expected instanceof Collection<?> collection && isAllMatcherGroup(normalized)) {
            return matchesAllMatcherGroup(request, collection);
        }
        if (expected instanceof Collection<?> collection && isAnyMatcherGroup(normalized)) {
            return matchesAnyMatcherGroup(request, collection);
        }
        if (expected instanceof Map<?, ?> map) {
            return matchesNestedMatcher(request, normalizedKey, map);
        }
        if (expected instanceof Collection<?> collection) {
            String actual = matcherValue(request, normalizedKey);
            return matchesExpectedValue(actual, collection);
        }
        String actual = matcherValue(request, normalizedKey);
        return matchesExpectedValue(actual, expected);
    }

    private RequestMatchStatus matchesMatcherMap(RateLimitRequest request, Map<?, ?> matcher) {
        for (Map.Entry<?, ?> entry : matcher.entrySet()) {
            RequestMatchStatus status = matchesMatcherEntry(
                    request,
                    String.valueOf(entry.getKey()),
                    entry.getValue());
            if (status != RequestMatchStatus.MATCHED) {
                return status;
            }
        }
        return RequestMatchStatus.MATCHED;
    }

    private RequestMatchStatus matchesAllMatcherGroup(RateLimitRequest request, Collection<?> matchers) {
        for (Object matcher : matchers) {
            if (!(matcher instanceof Map<?, ?> map)) {
                return RequestMatchStatus.UNSUPPORTED;
            }
            RequestMatchStatus status = matchesMatcherMap(request, map);
            if (status != RequestMatchStatus.MATCHED) {
                return status;
            }
        }
        return RequestMatchStatus.MATCHED;
    }

    private RequestMatchStatus matchesAnyMatcherGroup(RateLimitRequest request, Collection<?> matchers) {
        boolean unsupported = false;
        for (Object matcher : matchers) {
            if (!(matcher instanceof Map<?, ?> map)) {
                unsupported = true;
                continue;
            }
            RequestMatchStatus status = matchesMatcherMap(request, map);
            if (status == RequestMatchStatus.MATCHED) {
                return RequestMatchStatus.MATCHED;
            }
            if (status == RequestMatchStatus.UNSUPPORTED) {
                unsupported = true;
            }
        }
        return unsupported ? RequestMatchStatus.UNSUPPORTED : RequestMatchStatus.NOT_MATCHED;
    }

    private RequestMatchStatus matchesNestedMatcher(RateLimitRequest request, String key, Map<?, ?> expected) {
        String normalized = normalizeEnum(key);
        if (isAttributeGroup(normalized)) {
            for (Map.Entry<?, ?> entry : expected.entrySet()) {
                String actual = switch (normalized) {
                    case "HEADER", "HEADERS" -> request.header(String.valueOf(entry.getKey()));
                    case "GRPC_METADATA", "GRPCMETADATA", "METADATA", "METADATAS" -> request.metadata(String.valueOf(entry.getKey()));
                    case "ATTRIBUTE", "ATTRIBUTES" -> request.attribute(String.valueOf(entry.getKey()));
                    default -> "";
                };
                RequestMatchStatus status = matchesExpectedValue(actual, entry.getValue());
                if (status != RequestMatchStatus.MATCHED) {
                    return status;
                }
            }
            return RequestMatchStatus.MATCHED;
        }
        if (!isOperatorMatcher(expected)) {
            return RequestMatchStatus.UNSUPPORTED;
        }
        return matchesOperatorValue(matcherValue(request, key), expected);
    }

    private static boolean isAllMatcherGroup(String normalizedKey) {
        return "ALL".equals(normalizedKey)
                || "ALL_OF".equals(normalizedKey)
                || "ALLOF".equals(normalizedKey)
                || "AND".equals(normalizedKey);
    }

    private static boolean isAnyMatcherGroup(String normalizedKey) {
        return "ANY".equals(normalizedKey)
                || "ANY_OF".equals(normalizedKey)
                || "ANYOF".equals(normalizedKey)
                || "OR".equals(normalizedKey);
    }

    private static boolean isAttributeGroup(String normalizedKey) {
        return switch (normalizedKey) {
            case "HEADER", "HEADERS", "GRPC_METADATA", "GRPCMETADATA", "METADATA", "METADATAS", "ATTRIBUTE", "ATTRIBUTES" -> true;
            default -> false;
        };
    }

    private static boolean isOperatorMatcher(Map<?, ?> expected) {
        for (Map.Entry<?, ?> entry : expected.entrySet()) {
            if (!isMatchOperator(String.valueOf(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMatchOperator(String operator) {
        if (MATCH_OPERATOR_KEYS.contains(operator)) {
            return true;
        }
        return switch (normalizeEnum(operator)) {
            case "VALUE",
                    "EQUALS",
                    "EQUAL",
                    "EQ",
                    "IS",
                    "NOT_EQUALS",
                    "NOTEQUALS",
                    "NOT_EQUAL",
                    "NOTEQUAL",
                    "NE",
                    "IN",
                    "ONE_OF",
                    "ONEOF",
                    "ANY_OF",
                    "ANYOF",
                    "NOT_IN",
                    "NOTIN",
                    "CONTAINS",
                    "PREFIX",
                    "STARTS_WITH",
                    "STARTSWITH",
                    "SUFFIX",
                    "ENDS_WITH",
                    "ENDSWITH",
                    "REGEX",
                    "PATTERN",
                    "MATCHES",
                    "REQUIRED",
                    "EXISTS",
                    "IGNORE_CASE",
                    "IGNORECASE",
                    "CASE_INSENSITIVE",
                    "CASEINSENSITIVE" -> true;
            default -> false;
        };
    }

    private static RequestMatchStatus matchesExpectedValue(String actual, Object expected) {
        if (expected == null) {
            return RequestMatchStatus.MATCHED;
        }
        if (expected instanceof Map<?, ?> map) {
            if (!isOperatorMatcher(map)) {
                return RequestMatchStatus.UNSUPPORTED;
            }
            return matchesOperatorValue(actual, map);
        }
        if (expected instanceof Collection<?> collection) {
            for (Object value : collection) {
                if (value instanceof Map<?, ?>) {
                    return RequestMatchStatus.UNSUPPORTED;
                }
                if (enumOrTextEquals(actual, String.valueOf(value))) {
                    return RequestMatchStatus.MATCHED;
                }
            }
            return RequestMatchStatus.NOT_MATCHED;
        }
        return enumOrTextEquals(actual, String.valueOf(expected))
                ? RequestMatchStatus.MATCHED
                : RequestMatchStatus.NOT_MATCHED;
    }

    private static RequestMatchStatus matchesOperatorValue(String actual, Map<?, ?> expected) {
        String value = actual == null ? "" : actual.trim();
        boolean ignoreCase = booleanValue(valueByKey(expected, "ignoreCase", "ignore_case", "caseInsensitive", "case_insensitive"));
        for (Map.Entry<?, ?> entry : expected.entrySet()) {
            String operator = normalizeEnum(String.valueOf(entry.getKey()));
            Object expectedValue = entry.getValue();
            if ("IGNORE_CASE".equals(operator)
                    || "IGNORECASE".equals(operator)
                    || "CASE_INSENSITIVE".equals(operator)
                    || "CASEINSENSITIVE".equals(operator)) {
                continue;
            }
            RequestMatchStatus status = switch (operator) {
                case "REQUIRED", "EXISTS" -> matchesExists(value, booleanValue(expectedValue));
                case "VALUE", "EQUALS", "EQUAL", "EQ", "IS" ->
                        textEquals(value, String.valueOf(expectedValue), ignoreCase)
                                ? RequestMatchStatus.MATCHED
                                : RequestMatchStatus.NOT_MATCHED;
                case "NOT_EQUALS", "NOTEQUALS", "NOT_EQUAL", "NOTEQUAL", "NE" ->
                        textEquals(value, String.valueOf(expectedValue), ignoreCase)
                                ? RequestMatchStatus.NOT_MATCHED
                                : RequestMatchStatus.MATCHED;
                case "IN", "ONE_OF", "ONEOF", "ANY_OF", "ANYOF" -> matchesAnyValue(value, expectedValue, ignoreCase)
                        ? RequestMatchStatus.MATCHED
                        : RequestMatchStatus.NOT_MATCHED;
                case "NOT_IN", "NOTIN" -> matchesAnyValue(value, expectedValue, ignoreCase)
                        ? RequestMatchStatus.NOT_MATCHED
                        : RequestMatchStatus.MATCHED;
                case "CONTAINS" -> containsValue(value, String.valueOf(expectedValue), ignoreCase)
                        ? RequestMatchStatus.MATCHED
                        : RequestMatchStatus.NOT_MATCHED;
                case "PREFIX", "STARTS_WITH", "STARTSWITH" -> startsWithValue(value, String.valueOf(expectedValue), ignoreCase)
                        ? RequestMatchStatus.MATCHED
                        : RequestMatchStatus.NOT_MATCHED;
                case "SUFFIX", "ENDS_WITH", "ENDSWITH" -> endsWithValue(value, String.valueOf(expectedValue), ignoreCase)
                        ? RequestMatchStatus.MATCHED
                        : RequestMatchStatus.NOT_MATCHED;
                case "REGEX", "PATTERN", "MATCHES" -> matchesRegex(value, String.valueOf(expectedValue), ignoreCase);
                default -> RequestMatchStatus.UNSUPPORTED;
            };
            if (status != RequestMatchStatus.MATCHED) {
                return status;
            }
        }
        return RequestMatchStatus.MATCHED;
    }

    private static RequestMatchStatus matchesExists(String actual, boolean expectedExists) {
        boolean exists = actual != null && !actual.isBlank();
        return exists == expectedExists ? RequestMatchStatus.MATCHED : RequestMatchStatus.NOT_MATCHED;
    }

    private static boolean matchesAnyValue(String actual, Object expected, boolean ignoreCase) {
        if (expected instanceof Collection<?> collection) {
            return collection.stream().anyMatch(val -> textEquals(actual, String.valueOf(val), ignoreCase));
        }
        return textEquals(actual, String.valueOf(expected), ignoreCase);
    }

    private static boolean textEquals(String actual, String expected, boolean ignoreCase) {
        String left = actual == null ? "" : actual.trim();
        String right = expected == null ? "" : expected.trim();
        return ignoreCase ? left.equalsIgnoreCase(right) : enumOrTextEquals(left, right);
    }

    private static boolean containsValue(String actual, String expected, boolean ignoreCase) {
        return ignoreCase
                ? actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT))
                : actual.contains(expected);
    }

    private static boolean startsWithValue(String actual, String expected, boolean ignoreCase) {
        return ignoreCase
                ? actual.toLowerCase(Locale.ROOT).startsWith(expected.toLowerCase(Locale.ROOT))
                : actual.startsWith(expected);
    }

    private static boolean endsWithValue(String actual, String expected, boolean ignoreCase) {
        return ignoreCase
                ? actual.toLowerCase(Locale.ROOT).endsWith(expected.toLowerCase(Locale.ROOT))
                : actual.endsWith(expected);
    }

    private static RequestMatchStatus matchesRegex(String actual, String regex, boolean ignoreCase) {
        try {
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
            return Pattern.compile(regex, flags).matcher(actual).matches()
                    ? RequestMatchStatus.MATCHED
                    : RequestMatchStatus.NOT_MATCHED;
        } catch (PatternSyntaxException e) {
            return RequestMatchStatus.UNSUPPORTED;
        }
    }

    private static String matcherValue(RateLimitRequest request, String key) {
        return switch (normalizeEnum(key)) {
            case "APPLICATION", "APPLICATION_CODE", "APPLICATIONCODE" -> request.applicationCode();
            case "SERVICE", "TARGET_SERVICE", "TARGETSERVICE" -> request.targetService();
            case "RESOURCE", "PATH", "HTTP_PATH", "ROUTE", "ROUTES" -> request.resource();
            case "METHOD", "METHODS", "GRPC_METHOD" -> request.method();
            case "ENDPOINT" -> request.endpoint();
            case "TENANT", "TENANT_ID", "TENANTID" -> request.tenantId();
            case "USER", "USER_ID", "USERID" -> request.userId();
            case "CALLER" -> request.caller();
            case "API_KEY", "APIKEY" -> request.apiKey();
            case "REMOTE_IP", "REMOTEIP", "IP" -> request.remoteIp();
            case "MODEL_REQUEST", "MODELREQUEST" -> request.modelRequest();
            case "MODEL_TOKEN", "MODEL_TOKENS", "MODELTOKEN", "MODELTOKENS" -> request.modelTokens() > 0 ? Long.toString(request.modelTokens()) : "";
            case "MODEL_COST", "MODELCOST" -> request.modelCost() > 0 ? Long.toString(request.modelCost()) : "";
            case "QUOTA_KEY", "QUOTAKEY" -> request.quotaKey();
            case "UNIT" -> request.unit();
            default -> request.attribute(key);
        };
    }

    private String resolveKeyExtractor(RateLimitRequest request) {
        List<Map<String, Object>> extractors = keyExtractorEntries();
        if (extractors.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(":");
        for (Map<String, Object> extractor : extractors) {
            String value = extractorValue(request, extractor);
            if (!value.isBlank()) {
                joiner.add(extractorName(extractor) + "=" + value);
            }
        }
        return joiner.toString();
    }

    private boolean hasUnsupportedKeyExtractorSchema() {
        return !keyExtractor.isEmpty() && keyExtractorEntries().isEmpty();
    }

    private List<Map<String, Object>> keyExtractorEntries() {
        if (keyExtractor.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (String key : KEY_EXTRACTOR_COLLECTION_KEYS) {
            appendExtractorEntries(entries, valueByKey(keyExtractor, key));
        }
        if (!entries.isEmpty()) {
            return List.copyOf(entries);
        }
        if (isDirectExtractor(keyExtractor)) {
            return List.of(keyExtractor);
        }
        if (keyExtractor.values().stream().allMatch(Map.class::isInstance)) {
            keyExtractor.forEach((name, val) -> {
                Map<String, Object> extractor = copyObjectMap((Map<?, ?>) val);
                if (!extractor.containsKey("name")) {
                    LinkedHashMap<String, Object> named = new LinkedHashMap<>(extractor);
                    named.put("name", name);
                    extractor = copyObjectMap(named);
                }
                entries.add(extractor);
            });
        }
        return List.copyOf(entries);
    }

    private static String extractorValue(RateLimitRequest request, Map<String, Object> extractor) {
        String source = extractorSource(extractor);
        String key = extractorLookupKey(extractor);
        String value = switch (source) {
            case "HEADER" -> request.header(key);
            case "GRPC_METADATA" -> request.metadata(key);
            case "HTTP_PATH", "RESOURCE" -> request.resource();
            case "GRPC_METHOD", "METHOD" -> request.method();
            case "REMOTE_IP", "IP" -> request.remoteIp();
            case "TENANT" -> request.tenantId();
            case "USER" -> request.userId();
            case "CALLER" -> request.caller();
            case "API_KEY" -> request.apiKey();
            case "ENDPOINT" -> request.endpoint();
            case "TOPIC" -> request.attribute(defaultText(key, "topic"));
            case "MODEL_REQUEST" -> firstText(request.modelRequest(), request.attribute(defaultText(key, "modelRequest")));
            case "MODEL_TOKEN" -> request.modelTokens() > 0
                    ? Long.toString(request.modelTokens())
                    : request.attribute(defaultText(key, "modelTokens"));
            case "MODEL_COST" -> request.modelCost() > 0
                    ? Long.toString(request.modelCost())
                    : request.attribute(defaultText(key, "modelCost"));
            case "CUSTOM_KEY" -> request.attribute(key);
            default -> "";
        };
        value = value == null ? "" : value.trim();
        if (booleanValue(extractor.get("normalize"))) {
            return value.toLowerCase(Locale.ROOT);
        }
        return value;
    }

    private static String extractorSource(Map<String, Object> extractor) {
        String source = text(valueByKey(extractor, "source", "type", "from"));
        if (!source.isBlank()) {
            return normalizeEnum(source);
        }
        if (!text(valueByKey(extractor, "header", "headerName", "header_name")).isBlank()) {
            return "HEADER";
        }
        if (!text(valueByKey(extractor, "metadata", "metadataName", "metadata_name", "grpcMetadata", "grpc_metadata")).isBlank()) {
            return "GRPC_METADATA";
        }
        if (!extractorLookupKey(extractor).isBlank()) {
            return "CUSTOM_KEY";
        }
        return "";
    }

    private static String extractorName(Map<String, Object> extractor) {
        String name = text(valueByKey(extractor, "name", "alias"));
        if (!name.isBlank()) {
            return name;
        }
        String key = extractorLookupKey(extractor);
        if (!key.isBlank()) {
            return key;
        }
        return extractorSource(extractor).toLowerCase(Locale.ROOT);
    }

    private static String extractorLookupKey(Map<String, Object> extractor) {
        String key = text(valueByKey(
                extractor,
                "key",
                "field",
                "attribute",
                "customKey",
                "custom_key",
                "header",
                "headerName",
                "header_name",
                "metadata",
                "metadataName",
                "metadata_name",
                "grpcMetadata",
                "grpc_metadata"));
        if (!key.isBlank()) {
            return key;
        }
        return text(valueByKey(extractor, "name"));
    }

    private static void appendExtractorEntries(List<Map<String, Object>> entries, Object value) {
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    entries.add(copyObjectMap(map));
                }
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            entries.add(copyObjectMap(map));
        }
    }

    private static boolean isDirectExtractor(Map<String, Object> extractor) {
        return valueByKey(
                        extractor,
                        "source",
                        "type",
                        "from",
                        "key",
                        "field",
                        "attribute",
                        "customKey",
                        "custom_key",
                        "header",
                        "headerName",
                        "header_name",
                        "metadata",
                        "metadataName",
                        "metadata_name",
                        "grpcMetadata",
                        "grpc_metadata",
                        "name")
                != null;
    }

    private static Map<String, Object> copyObjectMap(Map<?, ?> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> copied = new LinkedHashMap<>();
        values.forEach((key, val) -> {
            if (key != null && val != null) {
                copied.put(String.valueOf(key), copyObject(val));
            }
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Object copyObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            return copyObjectMap(map);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copied = new ArrayList<>();
            for (Object item : collection) {
                if (item != null) {
                    copied.add(copyObject(item));
                }
            }
            return List.copyOf(copied);
        }
        return value;
    }

    private static Object valueByKey(Map<?, ?> values, String... keys) {
        if (values == null || values.isEmpty() || keys == null || keys.length == 0) {
            return null;
        }
        for (String expected : keys) {
            String normalizedExpected = normalizeEnum(expected);
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (normalizeEnum(String.valueOf(entry.getKey())).equals(normalizedExpected)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
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

    private static String normalizeEnum(String value) {
        return value == null ? "" : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool
                ? bool
                : "true".equalsIgnoreCase(text(value));
    }

    private static boolean enumOrTextEquals(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        String left = first.trim();
        String right = second.trim();
        return left.equals(right) || normalizeEnum(left).equals(normalizeEnum(right));
    }
}
