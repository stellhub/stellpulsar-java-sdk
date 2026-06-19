package io.github.stellhub.stellpulsar.client.topology;

import java.util.Locale;
import java.util.Map;

public record PulsarInstance(
        String instanceId,
        String host,
        int port,
        int priority,
        int weight,
        String zone,
        String version,
        String ruleRevision,
        String state,
        Map<String, String> metadata) {

    public PulsarInstance {
        instanceId = requireText(instanceId, "instanceId");
        host = requireText(host, "host");
        zone = defaultText(zone);
        version = defaultText(version);
        ruleRevision = defaultText(ruleRevision);
        state = defaultText(state).isBlank() ? "UP" : state;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * 判断实例是否可作为新 quota owner。
     */
    public boolean ownerCandidate() {
        return "UP".equals(state.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * 返回 owner 计算使用的有效权重。
     */
    public int effectiveWeight() {
        return weight <= 0 ? 100 : weight;
    }

    /**
     * 返回 gRPC 连接地址。
     */
    public String authority() {
        return host + ":" + port;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String defaultText(String value) {
        return value == null ? "" : value;
    }
}
