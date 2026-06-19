package io.github.stellhub.stellpulsar.client.quota;

import io.github.stellhub.stellpulsar.client.rule.DistributedRateLimitRule;
import io.github.stellhub.stellpulsar.client.topology.SelectedOwner;
import java.util.Map;
import java.util.Objects;

public record AcquireQuotaCommand(
        String requestId,
        String applicationCode,
        String clientId,
        DistributedRateLimitRule rule,
        String quotaKey,
        long cost,
        Map<String, String> attributes,
        SelectedOwner owner) {

    public AcquireQuotaCommand {
        requestId = requireText(requestId, "requestId");
        applicationCode = requireText(applicationCode, "applicationCode");
        clientId = requireText(clientId, "clientId");
        rule = Objects.requireNonNull(rule, "rule must not be null");
        quotaKey = requireText(quotaKey, "quotaKey");
        cost = cost <= 0 ? 1 : cost;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        owner = Objects.requireNonNull(owner, "owner must not be null");
    }

    /**
     * 返回请求使用的 topology revision。
     */
    public String topologyRevision() {
        return owner.topology().topologyRevision();
    }

    /**
     * 返回请求目标实例 ID。
     */
    public String targetInstanceId() {
        return owner.instance().instanceId();
    }

    /**
     * 返回 shard hash。
     */
    public String shardHash() {
        return owner.shardHash();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
