package io.github.stellhub.stellpulsar.client.rule;

public interface RefreshableDistributedRateLimitRuleProvider extends DistributedRateLimitRuleProvider {

    /**
     * 请求底层规则源尽快刷新。
     */
    void refresh();
}
