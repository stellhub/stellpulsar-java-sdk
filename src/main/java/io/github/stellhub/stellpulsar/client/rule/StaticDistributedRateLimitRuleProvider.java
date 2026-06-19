package io.github.stellhub.stellpulsar.client.rule;

import io.github.stellhub.stellpulsar.client.model.RateLimitRequest;
import java.util.List;

public final class StaticDistributedRateLimitRuleProvider implements DistributedRateLimitRuleProvider {

    private final List<DistributedRateLimitRule> rules;

    public StaticDistributedRateLimitRuleProvider(List<DistributedRateLimitRule> rules) {
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * 返回静态规则集合。
     */
    @Override
    public List<DistributedRateLimitRule> find(RateLimitRequest request) {
        return rules.stream()
                .filter(rule -> rule.applicationCode().equals(request.applicationCode()))
                .toList();
    }
}
