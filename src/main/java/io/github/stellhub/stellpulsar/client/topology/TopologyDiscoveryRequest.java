package io.github.stellhub.stellpulsar.client.topology;

import java.util.List;
import java.util.Map;

public record TopologyDiscoveryRequest(
        String namespace,
        String applicationCode,
        String clientId,
        List<String> supportedProtocolVersions,
        Map<String, String> labels) {

    public TopologyDiscoveryRequest {
        namespace = namespace == null || namespace.isBlank() ? "default" : namespace;
        applicationCode = requireText(applicationCode, "applicationCode");
        clientId = requireText(clientId, "clientId");
        supportedProtocolVersions = supportedProtocolVersions == null
                ? List.of("stellpulsar.v1")
                : List.copyOf(supportedProtocolVersions);
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
