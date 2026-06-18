package io.github.stellhub.stellpulsar.client.model;

import java.util.Objects;

public record LimitCheckRequest(String key, int limit, int windowSeconds, int cost) {

    public LimitCheckRequest {
        Objects.requireNonNull(key, "key must not be null");
    }
}
