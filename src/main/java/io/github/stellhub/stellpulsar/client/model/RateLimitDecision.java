package io.github.stellhub.stellpulsar.client.model;

public enum RateLimitDecision {
    UNSPECIFIED,
    ALLOWED,
    DENIED,
    RULE_STALE,
    SERVER_RULE_LAG,
    RULE_CONFLICT,
    RULE_NOT_FOUND,
    INVALID_REQUEST,
    NOT_OWNER,
    SHARD_MIGRATING,
    NO_MATCHING_RULE,
    FALLBACK_ALLOWED,
    FALLBACK_DENIED,
    CLIENT_ERROR
}
