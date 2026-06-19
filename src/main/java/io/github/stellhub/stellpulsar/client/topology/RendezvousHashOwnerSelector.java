package io.github.stellhub.stellpulsar.client.topology;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class RendezvousHashOwnerSelector implements OwnerSelector {

    /**
     * 根据 rendezvous_hash_v1 选择 owner。
     */
    @Override
    public SelectedOwner select(TopologySnapshot topology, String shardKey) {
        if (topology == null) {
            throw new IllegalArgumentException("topology must not be null");
        }
        if (!TopologySnapshot.RENDEZVOUS_HASH_V1.equals(topology.hashAlgorithm())) {
            throw new IllegalStateException("unsupported hash algorithm: " + topology.hashAlgorithm());
        }
        if (shardKey == null || shardKey.isBlank()) {
            throw new IllegalArgumentException("shardKey must not be blank");
        }
        List<PulsarInstance> candidates = topology.ownerCandidates();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("no routable StellPulsar instance");
        }
        PulsarInstance selected = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (PulsarInstance instance : candidates) {
            double score = score(topology.topologyRevision(), shardKey, instance);
            if (selected == null
                    || score > bestScore
                    || (Double.compare(score, bestScore) == 0
                    && instance.instanceId().compareTo(selected.instanceId()) < 0)) {
                selected = instance;
                bestScore = score;
            }
        }
        return new SelectedOwner(selected, topology, shardKey, shardHash(topology.topologyRevision(), shardKey));
    }

    /**
     * 计算 shard key。
     */
    public static String shardKey(String applicationCode, String ruleId, String quotaKey) {
        return trim(applicationCode) + ":" + trim(ruleId) + ":" + trim(quotaKey);
    }

    /**
     * 计算 sha256 十六进制摘要。
     */
    public static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    /**
     * 计算与服务端一致的 shard hash 诊断值。
     */
    public static String shardHash(String topologyRevision, String shardKey) {
        return sha256Hex(trim(topologyRevision) + "\n" + trim(shardKey)).substring(0, 16);
    }

    private static double score(String topologyRevision, String shardKey, PulsarInstance instance) {
        String input = trim(topologyRevision) + "\n" + trim(shardKey) + "\n" + instance.instanceId();
        byte[] bytes = sha256(input);
        long signed = ByteBuffer.wrap(bytes, 0, Long.BYTES).getLong();
        double unit = (Double.parseDouble(Long.toUnsignedString(signed)) + 1.0d)
                / 18446744073709551616.0d;
        if (unit >= 1.0d) {
            unit = Math.nextDown(1.0d);
        }
        return instance.effectiveWeight() / -Math.log(unit);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
