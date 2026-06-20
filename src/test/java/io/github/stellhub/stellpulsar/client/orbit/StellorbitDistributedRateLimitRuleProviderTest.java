package io.github.stellhub.stellpulsar.client.orbit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.stellhub.stellpulsar.client.model.FailPolicy;
import io.github.stellhub.stellpulsar.client.model.RateLimitRequest;
import io.github.stellhub.stellpulsar.client.rule.DistributedRateLimitRule;
import io.github.stellorbit.client.rule.GovernanceRule;
import io.github.stellorbit.client.rule.GovernanceRuleStatus;
import io.github.stellorbit.client.rule.GovernanceRuleType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StellorbitDistributedRateLimitRuleProviderTest {

    @Test
    void findConvertsOnlyGlobalCoordinationRateLimitRules() {
        GovernanceRule distributed = new GovernanceRule(
                "rule-a",
                "Orders",
                "config-a",
                GovernanceRuleType.RATE_LIMIT,
                "order-service",
                GovernanceRuleStatus.ACTIVE,
                10,
                100L,
                "checksum-a",
                "{}",
                Map.of(
                        "limitMode", "QPS",
                        "limitType", "REQUEST",
                        "limitAlgorithm", "FIXED_WINDOW",
                        "trafficProtocol", "HTTP",
                        "executionLocation", "APPLICATION",
                        "coordinationMode", "GLOBAL_QUOTA",
                        "dimensions", List.of("tenantId", "resource"),
                        "quotaConfig", Map.of("quota", 100),
                        "windowConfig", Map.of("windowSeconds", 60),
                        "fallbackPolicy", Map.of("mode", "FAIL_CLOSED")));
        GovernanceRule local = new GovernanceRule(
                "rule-b",
                "Local",
                "config-b",
                GovernanceRuleType.RATE_LIMIT,
                "order-service",
                GovernanceRuleStatus.ACTIVE,
                20,
                100L,
                "checksum-b",
                "{}",
                Map.of(
                        "limitMode", "QPS",
                        "limitAlgorithm", "FIXED_WINDOW",
                        "coordinationMode", "LOCAL_ONLY"));
        GovernanceRule legacy = new GovernanceRule(
                "rule-c",
                "Legacy",
                "config-c",
                GovernanceRuleType.RATE_LIMIT,
                "order-service",
                GovernanceRuleStatus.ACTIVE,
                30,
                100L,
                "checksum-c",
                "{}",
                Map.of("limit", Map.of("mode", "DISTRIBUTED", "backend", "stellpulsar")));
        StellorbitDistributedRateLimitRuleProvider provider =
                new StellorbitDistributedRateLimitRuleProvider(query -> List.of(distributed, local, legacy));

        List<DistributedRateLimitRule> rules = provider.find(RateLimitRequest.builder()
                .applicationCode("order-service")
                .targetService("order-service")
                .tenantId("tenant-a")
                .resource("/orders")
                .build());

        assertEquals(1, rules.size());
        DistributedRateLimitRule rule = rules.getFirst();
        assertEquals("rule-a", rule.ruleId());
        assertEquals("100", rule.revision());
        assertEquals("QPS", rule.limitMode());
        assertEquals("REQUEST", rule.limitType());
        assertEquals("FIXED_WINDOW", rule.limitAlgorithm());
        assertEquals("HTTP", rule.trafficProtocol());
        assertEquals("GLOBAL_QUOTA", rule.coordinationMode());
        assertEquals(100, rule.quota());
        assertEquals(60, rule.windowSeconds());
        assertEquals(FailPolicy.FAIL_CLOSED, rule.failPolicy());
        assertEquals(List.of("tenantId", "resource"), rule.dimensions());
    }

    @Test
    void findConvertsHeaderKeyExtractor() {
        GovernanceRule headerRule = new GovernanceRule(
                "rule-header",
                "Header",
                "config-header",
                GovernanceRuleType.RATE_LIMIT,
                "order-service",
                GovernanceRuleStatus.ACTIVE,
                10,
                101L,
                "checksum-header",
                "{}",
                Map.of(
                        "limitMode", "HEADER",
                        "limitType", "HEADER",
                        "limitAlgorithm", "TOKEN_BUCKET",
                        "trafficProtocol", "HTTP",
                        "executionLocation", "APPLICATION",
                        "coordinationMode", "GLOBAL_SYNC",
                        "keyExtractor", Map.of(
                                "keys", List.of(Map.of(
                                        "name", "tenant",
                                        "source", "HEADER",
                                        "key", "X-Tenant",
                                        "required", true,
                                        "normalize", true))),
                        "quotaConfig", Map.of("quota", 50),
                        "windowConfig", Map.of("windowSeconds", 30)));
        StellorbitDistributedRateLimitRuleProvider provider =
                new StellorbitDistributedRateLimitRuleProvider(query -> List.of(headerRule));

        DistributedRateLimitRule rule = provider.find(RateLimitRequest.builder()
                        .applicationCode("order-service")
                        .targetService("order-service")
                        .resource("/orders")
                        .header("X-Tenant", "Tenant-A")
                        .build())
                .getFirst();

        assertEquals("HEADER", rule.limitMode());
        assertEquals("tenant=tenant-a", rule.resolveQuotaKey(RateLimitRequest.builder()
                .applicationCode("order-service")
                .targetService("order-service")
                .resource("/orders")
                .header("x-tenant", "Tenant-A")
                .build()));
    }
}
