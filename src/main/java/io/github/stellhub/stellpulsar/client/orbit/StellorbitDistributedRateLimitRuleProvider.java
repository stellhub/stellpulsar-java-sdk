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
import java.util.Collection;
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
        Map<String, Object> content = rule.content();
        String mode = stringValue(firstNonNull(
                nested(content, "limit", "mode"),
                nested(content, "rateLimit", "mode"),
                content.get("mode")));
        String backend = stringValue(firstNonNull(
                nested(content, "limit", "backend"),
                nested(content, "rateLimit", "backend"),
                content.get("backend")));
        return "distributed".equals(normalize(mode)) || "stellpulsar".equals(normalize(backend));
    }

    private DistributedRateLimitRule toDistributedRule(String applicationCode, GovernanceRule rule) {
        Map<String, Object> content = rule.content();
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
                .algorithm(stringValue(firstNonNull(
                        nested(content, "limit", "algorithm"),
                        nested(content, "rateLimit", "algorithm"),
                        content.get("algorithm"))))
                .quota(longValue(firstNonNull(
                        nested(content, "limit", "quota"),
                        nested(content, "rateLimit", "quota"),
                        content.get("quota"))))
                .windowSeconds(longValue(firstNonNull(
                        nested(content, "limit", "windowSeconds"),
                        nested(content, "limit", "window_seconds"),
                        nested(content, "rateLimit", "windowSeconds"),
                        content.get("windowSeconds"),
                        content.get("window_seconds"))))
                .burst(longValue(firstNonNull(
                        nested(content, "limit", "burst"),
                        nested(content, "rateLimit", "burst"),
                        content.get("burst"))))
                .dimensions(stringList(firstNonNull(
                        nested(content, "limit", "dimensions"),
                        nested(content, "rateLimit", "dimensions"),
                        content.get("dimensions"))))
                .cost(longValue(firstNonNull(
                        nested(content, "limit", "cost"),
                        nested(content, "rateLimit", "cost"),
                        content.get("cost"),
                        1)))
                .failPolicy(FailPolicy.parse(stringValue(firstNonNull(
                        nested(content, "limit", "failPolicy"),
                        nested(content, "limit", "fail_policy"),
                        nested(content, "rateLimit", "failPolicy"),
                        content.get("failPolicy"),
                        content.get("fail_policy"))), FailPolicy.FAIL_OPEN))
                .attributes(stringAttributes(content))
                .build();
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

    private static Object nested(Map<String, Object> content, String first, String second) {
        Object value = content.get(first);
        if (value instanceof Map<?, ?> nested) {
            return nested.get(second);
        }
        return null;
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

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }
}
