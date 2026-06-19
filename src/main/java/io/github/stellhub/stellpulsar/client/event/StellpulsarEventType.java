package io.github.stellhub.stellpulsar.client.event;

public enum StellpulsarEventType {
    PERMITTED,
    REJECTED,
    FALLBACK_PERMITTED,
    FALLBACK_REJECTED,
    RULE_CONFLICT,
    TOPOLOGY_REDIRECT,
    ERROR
}
