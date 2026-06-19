package io.github.stellhub.stellpulsar.client.topology;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record TopologySnapshot(
        String protocolVersion,
        String topologyRevision,
        long expiresAtUnixMs,
        String hashAlgorithm,
        List<PulsarInstance> instances) {

    public static final String RENDEZVOUS_HASH_V1 = "rendezvous_hash_v1";

    public TopologySnapshot {
        protocolVersion = defaultText(protocolVersion);
        topologyRevision = requireText(topologyRevision, "topologyRevision");
        hashAlgorithm = defaultText(hashAlgorithm).isBlank() ? RENDEZVOUS_HASH_V1 : hashAlgorithm;
        instances = instances == null ? List.of() : List.copyOf(instances);
    }

    /**
     * 创建一个只含单实例的本地拓扑。
     */
    public static TopologySnapshot single(PulsarInstance instance) {
        return new TopologySnapshot(
                "stellpulsar.v1",
                "local",
                Long.MAX_VALUE,
                RENDEZVOUS_HASH_V1,
                List.of(instance));
    }

    /**
     * 判断拓扑缓存是否过期。
     */
    public boolean expired(Clock clock) {
        return expiresAtUnixMs > 0 && expiresAtUnixMs < clock.millis();
    }

    /**
     * 返回可作为新 owner 的 UP 实例集合。
     */
    public List<PulsarInstance> ownerCandidates() {
        return instances.stream()
                .filter(PulsarInstance::ownerCandidate)
                .sorted(Comparator.comparing(PulsarInstance::instanceId))
                .toList();
    }

    /**
     * 返回拓扑诊断属性。
     */
    public Map<String, String> attributes() {
        return Map.of(
                "topologyRevision", topologyRevision,
                "hashAlgorithm", hashAlgorithm,
                "instanceCount", Integer.toString(instances.size()));
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
