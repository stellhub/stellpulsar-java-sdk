package io.github.stellhub.stellpulsar.client.model;

import java.util.Map;

public record RateLimitResult(
        boolean permitted,
        boolean limited,
        RateLimitDecision decision,
        String ruleId,
        String ruleRevision,
        String ruleChecksum,
        long remaining,
        long resetAtUnixMs,
        long retryAfterMs,
        String reason,
        boolean fallback,
        String errorCode,
        Map<String, String> attributes) {

    public RateLimitResult {
        limited = !permitted;
        decision = decision == null ? RateLimitDecision.UNSPECIFIED : decision;
        ruleId = defaultText(ruleId);
        ruleRevision = defaultText(ruleRevision);
        ruleChecksum = defaultText(ruleChecksum);
        reason = defaultText(reason);
        errorCode = defaultText(errorCode);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * 创建无匹配规则的放行结果。
     */
    public static RateLimitResult noMatchingRule(String requestId) {
        return new RateLimitResult(
                true,
                false,
                RateLimitDecision.NO_MATCHING_RULE,
                "",
                "",
                "",
                -1,
                0,
                0,
                "no_matching_distributed_rate_limit_rule",
                false,
                "",
                Map.of("requestId", requestId));
    }

    /**
     * 创建远端允许结果。
     */
    public static RateLimitResult allowed(
            String ruleId,
            String ruleRevision,
            String ruleChecksum,
            long remaining,
            long resetAtUnixMs,
            String reason) {
        return new RateLimitResult(
                true,
                false,
                RateLimitDecision.ALLOWED,
                ruleId,
                ruleRevision,
                ruleChecksum,
                remaining,
                resetAtUnixMs,
                0,
                reason,
                false,
                "",
                Map.of());
    }

    /**
     * 创建远端拒绝结果。
     */
    public static RateLimitResult denied(
            String ruleId,
            String ruleRevision,
            String ruleChecksum,
            long remaining,
            long resetAtUnixMs,
            long retryAfterMs,
            String reason) {
        return new RateLimitResult(
                false,
                true,
                RateLimitDecision.DENIED,
                ruleId,
                ruleRevision,
                ruleChecksum,
                remaining,
                resetAtUnixMs,
                retryAfterMs,
                reason,
                false,
                "",
                Map.of());
    }

    /**
     * 创建降级结果。
     */
    public static RateLimitResult fallback(
            FailPolicy failPolicy,
            RateLimitDecision sourceDecision,
            String ruleId,
            String ruleRevision,
            String ruleChecksum,
            String reason,
            String errorCode) {
        boolean permitted = failPolicy.fallbackPermitted();
        return new RateLimitResult(
                permitted,
                !permitted,
                permitted ? RateLimitDecision.FALLBACK_ALLOWED : RateLimitDecision.FALLBACK_DENIED,
                ruleId,
                ruleRevision,
                ruleChecksum,
                -1,
                0,
                0,
                reason,
                true,
                errorCode == null || errorCode.isBlank() ? sourceDecision.name() : errorCode,
                Map.of("sourceDecision", sourceDecision.name()));
    }

    private static String defaultText(String value) {
        return value == null ? "" : value;
    }
}
