package io.github.stellhub.stellpulsar.client.transport.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.grpc.Metadata;
import org.junit.jupiter.api.Test;

class GrpcStellpulsarTransportTest {

    @Test
    void authorizationMetadataUsesServerAcceptedKeys() {
        Metadata metadata = GrpcStellpulsarTransport.authorizationMetadata("local-token");

        assertEquals("Bearer local-token", metadata.get(GrpcStellpulsarTransport.AUTHORIZATION_KEY));
        assertEquals("local-token", metadata.get(GrpcStellpulsarTransport.STELLPULSAR_TOKEN_KEY));
    }
}
