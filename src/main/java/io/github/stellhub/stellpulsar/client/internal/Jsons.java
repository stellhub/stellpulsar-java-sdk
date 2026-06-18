package io.github.stellhub.stellpulsar.client.internal;

import io.github.stellhub.stellpulsar.client.model.LimitCheckRequest;

public final class Jsons {

    private Jsons() {
    }

    public static String limitCheckRequest(LimitCheckRequest request) {
        return "{"
                + "\"key\":\"" + escape(request.key()) + "\","
                + "\"limit\":" + request.limit() + ","
                + "\"windowSeconds\":" + request.windowSeconds() + ","
                + "\"cost\":" + request.cost()
                + "}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
