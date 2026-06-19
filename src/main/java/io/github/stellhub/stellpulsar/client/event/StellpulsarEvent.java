package io.github.stellhub.stellpulsar.client.event;

import io.github.stellhub.stellpulsar.client.model.RateLimitResult;
import java.time.Instant;
import java.util.Map;

public record StellpulsarEvent(
        StellpulsarEventType type,
        String requestId,
        String ruleId,
        RateLimitResult result,
        Instant occurredAt,
        Map<String, String> attributes) {

    public StellpulsarEvent {
        type = type == null ? StellpulsarEventType.ERROR : type;
        requestId = requestId == null ? "" : requestId;
        ruleId = ruleId == null ? "" : ruleId;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * 创建限流结果事件。
     */
    public static StellpulsarEvent fromResult(
            StellpulsarEventType type,
            String requestId,
            String ruleId,
            RateLimitResult result) {
        return new StellpulsarEvent(type, requestId, ruleId, result, Instant.now(), Map.of());
    }
}
