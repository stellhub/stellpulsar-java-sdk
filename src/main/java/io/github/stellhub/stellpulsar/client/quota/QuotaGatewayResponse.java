package io.github.stellhub.stellpulsar.client.quota;

import io.github.stellhub.stellpulsar.client.model.RateLimitDecision;
import io.github.stellhub.stellpulsar.client.topology.PulsarInstance;

public record QuotaGatewayResponse(
        String requestId,
        RateLimitDecision decision,
        String ruleId,
        String ruleRevision,
        String ruleChecksum,
        long remaining,
        long resetAtUnixMs,
        long retryAfterMs,
        String reason,
        String topologyRevision,
        PulsarInstance ownerInstance,
        String hashAlgorithm) {

    public QuotaGatewayResponse {
        requestId = requestId == null ? "" : requestId;
        decision = decision == null ? RateLimitDecision.UNSPECIFIED : decision;
        ruleId = ruleId == null ? "" : ruleId;
        ruleRevision = ruleRevision == null ? "" : ruleRevision;
        ruleChecksum = ruleChecksum == null ? "" : ruleChecksum;
        reason = reason == null ? "" : reason;
        topologyRevision = topologyRevision == null ? "" : topologyRevision;
        hashAlgorithm = hashAlgorithm == null ? "" : hashAlgorithm;
    }

    /**
     * 创建允许响应。
     */
    public static QuotaGatewayResponse allowed(String requestId, String ruleId, String revision, String checksum) {
        return new QuotaGatewayResponse(
                requestId,
                RateLimitDecision.ALLOWED,
                ruleId,
                revision,
                checksum,
                -1,
                0,
                0,
                "allowed",
                "",
                null,
                "");
    }

    /**
     * 创建拒绝响应。
     */
    public static QuotaGatewayResponse denied(
            String requestId,
            String ruleId,
            String revision,
            String checksum,
            long retryAfterMs) {
        return new QuotaGatewayResponse(
                requestId,
                RateLimitDecision.DENIED,
                ruleId,
                revision,
                checksum,
                0,
                0,
                retryAfterMs,
                "rate_limited",
                "",
                null,
                "");
    }
}
