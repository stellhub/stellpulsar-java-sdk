package io.github.stellhub.stellpulsar.client.model;

import java.util.Locale;

public enum FailPolicy {
    FAIL_OPEN,
    FAIL_CLOSED;

    /**
     * 解析失败降级策略。
     */
    public static FailPolicy parse(String value, FailPolicy defaultPolicy) {
        if (value == null || value.isBlank()) {
            return defaultPolicy;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "OPEN", "FAIL_OPEN" -> FAIL_OPEN;
            case "CLOSED", "CLOSE", "FAIL_CLOSED" -> FAIL_CLOSED;
            default -> defaultPolicy;
        };
    }

    /**
     * 返回降级后是否放行。
     */
    public boolean fallbackPermitted() {
        return this == FAIL_OPEN;
    }
}
