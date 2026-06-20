package io.github.stellhub.stellpulsar.client.orbit;

import io.github.stellhub.stellpulsar.client.model.FailPolicy;
import io.github.stellhub.stellpulsar.client.model.RateLimitRequest;
import io.github.stellhub.stellpulsar.client.rule.DistributedRateLimitRule;
import io.github.stellhub.stellpulsar.client.rule.DistributedRateLimitRuleProvider;
import io.github.stellorbit.client.model.RateLimitRuleQuery;
import io.github.stellorbit.client.model.RequestContext;
import io.github.stellorbit.client.provider.RateLimitRuleProvider;
import io.github.stellorbit.client.rule.GovernanceRule;
import io.github.stellorbit.client.rule.GovernanceRuleType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class StellorbitDistributedRateLimitRuleProvider implements DistributedRateLimitRuleProvider {

    private static final String DEFAULT_SCHEMA_VERSION = "v1";

    private final RateLimitRuleProvider delegate;

    public StellorbitDistributedRateLimitRuleProvider(RateLimitRuleProvider delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * 查询并转换 StellOrbit 分布式限流规则。
     */
    @Override
    public List<DistributedRateLimitRule> find(RateLimitRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        RateLimitRuleQuery query = new RateLimitRuleQuery(
                request.targetService(),
                request.quotaKey(),
                RequestContext.builder()
                        .tenantId(request.tenantId())
                        .quotaKey(request.quotaKey())
                        .attributes(request.attributeView())
                        .build());
        return delegate.find(query).stream()
                .filter(this::isDistributedRateLimitRule)
                .map(rule -> toDistributedRule(request.applicationCode(), rule))
                .toList();
    }

    private boolean isDistributedRateLimitRule(GovernanceRule rule) {
        if (rule == null || !rule.active() || rule.ruleType() != GovernanceRuleType.RATE_LIMIT) {
            return false;
        }
        return isDistributedCoordinationMode(stringValue(rule.content().get("coordinationMode")));
    }

    private DistributedRateLimitRule toDistributedRule(String applicationCode, GovernanceRule rule) {
        Map<String, Object> content = rule.content();
        Map<String, Object> quotaConfig = objectMap(content.get("quotaConfig"));
        Map<String, Object> windowConfig = objectMap(content.get("windowConfig"));
        Map<String, Object> burstConfig = objectMap(content.get("burstConfig"));
        Map<String, Object> fallbackPolicy = objectMap(content.get("fallbackPolicy"));
        return DistributedRateLimitRule.builder()
                .applicationCode(applicationCode)
                .ruleId(rule.ruleId())
                .ruleName(rule.ruleName())
                .revision(Long.toString(rule.revision()))
                .checksum(rule.checksum())
                .schemaVersion(stringValue(firstNonNull(
                        content.get("schemaVersion"),
                        content.get("schema_version"),
                        DEFAULT_SCHEMA_VERSION)))
                .limitMode(stringValue(content.get("limitMode")))
                .limitType(stringValue(content.get("limitType")))
                .limitAlgorithm(stringValue(content.get("limitAlgorithm")))
                .trafficProtocol(stringValue(content.get("trafficProtocol")))
                .executionLocation(stringValue(content.get("executionLocation")))
                .coordinationMode(stringValue(content.get("coordinationMode")))
                .algorithm(stringValue(content.get("limitAlgorithm")))
                .quota(longValue(firstNonNull(
                        quotaConfig.get("quota"),
                        quotaConfig.get("limit"),
                        quotaConfig.get("capacity"))))
                .windowSeconds(longValue(firstNonNull(
                        windowConfig.get("windowSeconds"),
                        windowConfig.get("window_seconds"),
                        windowConfig.get("durationSeconds"),
                        windowConfig.get("duration_seconds"))))
                .burst(longValue(firstNonNull(
                        burstConfig.get("burst"),
                        burstConfig.get("capacity"),
                        quotaConfig.get("burst"))))
                .dimensions(stringList(content.get("dimensions")))
                .cost(longValue(firstNonNull(
                        content.get("cost"),
                        quotaConfig.get("cost"),
                        1)))
                .failPolicy(parseFailPolicy(fallbackPolicy))
                .requestMatcher(objectMap(content.get("requestMatcher")))
                .keyExtractor(objectMap(content.get("keyExtractor")))
                .quotaConfig(quotaConfig)
                .windowConfig(windowConfig)
                .burstConfig(burstConfig)
                .concurrencyConfig(objectMap(content.get("concurrencyConfig")))
                .hotspotConfig(objectMap(content.get("hotspotConfig")))
                .customPolicy(objectMap(content.get("customPolicy")))
                .modelLimitConfig(objectMap(content.get("modelLimitConfig")))
                .fallbackPolicy(fallbackPolicy)
                .responsePolicy(objectMap(content.get("responsePolicy")))
                .observabilityConfig(objectMap(content.get("observabilityConfig")))
                .shadowConfig(objectMap(content.get("shadowConfig")))
                .attributes(stringAttributes(content))
                .build();
    }

    private static FailPolicy parseFailPolicy(Map<String, Object> fallbackPolicy) {
        return FailPolicy.parse(stringValue(firstNonNull(
                fallbackPolicy.get("failPolicy"),
                fallbackPolicy.get("fail_policy"),
                fallbackPolicy.get("mode"),
                fallbackPolicy.get("strategy"),
                fallbackPolicy.get("policy"))), FailPolicy.FAIL_OPEN);
    }

    private static boolean isDistributedCoordinationMode(String value) {
        String normalized = normalizeEnum(value);
        return DistributedRateLimitRule.COORDINATION_MODE_GLOBAL_SYNC.equals(normalized)
                || DistributedRateLimitRule.COORDINATION_MODE_GLOBAL_QUOTA.equals(normalized);
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (key != null && item != null) {
                values.put(String.valueOf(key), copyObject(item));
            }
        });
        return Collections.unmodifiableMap(values);
    }

    private static Object copyObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            return objectMap(map);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> values = new ArrayList<>();
            for (Object item : collection) {
                if (item != null) {
                    values.add(copyObject(item));
                }
            }
            return List.copyOf(values);
        }
        return value;
    }

    private static Map<String, String> stringAttributes(Map<String, Object> content) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        content.forEach((key, value) -> {
            if (value instanceof String text && !text.isBlank()) {
                values.put(key, text);
            } else if (value instanceof Number || value instanceof Boolean) {
                values.put(key, String.valueOf(value));
            }
        });
        return values;
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0;
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return 0;
        }
        return Long.parseLong(text);
    }

    private static List<String> stringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .filter(text -> !text.isBlank())
                    .toList();
        }
        if (value == null) {
            return List.of();
        }
        return List.of(String.valueOf(value).split(",")).stream()
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .toList();
    }

    private static String normalizeEnum(String value) {
        return value == null ? "" : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }
}
