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
    void findConvertsOnlyDistributedRateLimitRules() {
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
                        "limit", Map.of(
                                "mode", "DISTRIBUTED",
                                "backend", "stellpulsar",
                                "algorithm", "fixed_window",
                                "quota", 100,
                                "windowSeconds", 60,
                                "dimensions", List.of("tenantId", "resource"),
                                "failPolicy", "FAIL_CLOSED")));
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
                Map.of("limit", Map.of("mode", "LOCAL")));
        StellorbitDistributedRateLimitRuleProvider provider =
                new StellorbitDistributedRateLimitRuleProvider(query -> List.of(distributed, local));

        List<DistributedRateLimitRule> rules = provider.find(RateLimitRequest.builder()
                .applicationCode("order-service")
                .targetService("order-service")
                .tenantId("tenant-a")
                .resource("/orders")
                .build());

        assertEquals(1, rules.size());
        assertEquals("rule-a", rules.getFirst().ruleId());
        assertEquals("100", rules.getFirst().revision());
        assertEquals(FailPolicy.FAIL_CLOSED, rules.getFirst().failPolicy());
        assertEquals(List.of("tenantId", "resource"), rules.getFirst().dimensions());
    }
}
