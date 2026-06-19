package io.github.stellhub.stellpulsar.client.rule;

import io.github.stellhub.stellpulsar.client.model.RateLimitRequest;
import java.util.List;

public interface DistributedRateLimitRuleProvider {

    /**
     * 查找当前请求匹配的分布式限流规则。
     */
    List<DistributedRateLimitRule> find(RateLimitRequest request);
}
