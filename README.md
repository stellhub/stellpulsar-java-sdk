# StellPulsar Java SDK

`stellpulsar-java-sdk` is the Java client SDK for [`stellhub/stellpulsar-service`](https://github.com/stellhub/stellpulsar-service).

The SDK is a Spring-free distributed quota client. It reads distributed rate limit rules through `stellorbit-java-sdk`, routes quota requests to the correct StellPulsar owner over gRPC, and exposes a framework-neutral `tryAcquire` API for integrations such as `stellflux`.

## Positioning

This repository does not implement a local rate limiter or a Spring interceptor. Its responsibilities are:

- Filter distributed rate limit rules from StellOrbit rule providers.
- Discover StellPulsar topology and calculate shard owners with `rendezvous_hash_v1`.
- Call `AcquireQuota` over gRPC and map service decisions into Java result models.
- Expose `RateLimitResult` so framework adapters can decide whether to return HTTP 429, throw an exception, or apply fallback logic.

Resilience4j is intentionally not a core dependency. If needed, it should be integrated in `stellflux` or a separate optional adapter module.

## Current Status

| Item | Value |
| --- | --- |
| Stability | Early development |
| Language | Java |
| Minimum Java version | 25 |
| Transport | gRPC |
| Rule source | `stellorbit-java-sdk` |
| Target service | `stellpulsar-service` |

## Installation

```xml
<dependency>
    <groupId>io.github.stellhub</groupId>
    <artifactId>stellpulsar-java-sdk</artifactId>
    <version>${stellpulsar-java-sdk.version}</version>
</dependency>
```

## Quick Start

```java
package example;

import io.github.stellhub.stellpulsar.client.DefaultStellpulsarClient;
import io.github.stellhub.stellpulsar.client.StellpulsarClient;
import io.github.stellhub.stellpulsar.client.StellpulsarClientOptions;
import io.github.stellhub.stellpulsar.client.model.RateLimitRequest;
import io.github.stellhub.stellpulsar.client.model.RateLimitResult;
import io.github.stellhub.stellpulsar.client.orbit.StellorbitDistributedRateLimitRuleProvider;
import io.github.stellhub.stellpulsar.client.topology.DefaultTopologyManager;
import io.github.stellhub.stellpulsar.client.topology.RendezvousHashOwnerSelector;
import io.github.stellhub.stellpulsar.client.topology.TopologyDiscoveryRequest;
import io.github.stellhub.stellpulsar.client.transport.grpc.GrpcStellpulsarTransport;
import io.github.stellorbit.client.StellorbitClient;

public class StellpulsarExample {

    public static void main(String[] args) {
        StellorbitClient orbitClient = createOrbitClient();
        orbitClient.start();

        GrpcStellpulsarTransport transport = GrpcStellpulsarTransport.builder()
                .discoveryAddress("127.0.0.1", 9090)
                .plaintext(true)
                .build();

        StellpulsarClient client = new DefaultStellpulsarClient(StellpulsarClientOptions.builder()
                .applicationCode("order-service")
                .clientId("order-service-jvm-1")
                .ruleProvider(new StellorbitDistributedRateLimitRuleProvider(orbitClient.rateLimits()))
                .topologyManager(new DefaultTopologyManager(
                        transport,
                        new TopologyDiscoveryRequest(
                                "default",
                                "order-service",
                                "order-service-jvm-1",
                                java.util.List.of("stellpulsar.v1"),
                                java.util.Map.of()),
                        new RendezvousHashOwnerSelector()))
                .quotaGateway(transport)
                .build());

        client.start();

        RateLimitResult result = client.tryAcquire(RateLimitRequest.builder()
                .applicationCode("order-service")
                .targetService("order-service")
                .resource("/api/orders")
                .method("POST")
                .tenantId("tenant-a")
                .quotaKey("tenant-a:/api/orders")
                .build());

        if (result.limited()) {
            System.out.println("rate limited, retryAfterMs=" + result.retryAfterMs());
            return;
        }

        System.out.println("request permitted, remaining=" + result.remaining());
    }

    private static StellorbitClient createOrbitClient() {
        throw new UnsupportedOperationException("Create StellorbitClient with your StellOrbit/StellNula options.");
    }
}
```

## API Surface

| Type | Responsibility |
| --- | --- |
| `StellpulsarClient.tryAcquire(RateLimitRequest)` | Attempts to acquire remote distributed quota. |
| `RateLimitResult` | Framework-neutral decision model with `permitted`, `limited`, `fallback`, `retryAfterMs`, and diagnostic fields. |
| `DistributedRateLimitRuleProvider` | Internal SDK rule SPI. |
| `StellorbitDistributedRateLimitRuleProvider` | Adapter from `stellorbit-java-sdk` rate limit rules to distributed quota rules. |
| `GrpcStellpulsarTransport` | gRPC implementation of topology discovery and quota acquisition ports. |

## Development

Run verification:

```bash
mvn test
```

## Documentation

- [ADR](docs/ADR.md)
- [stellpulsar-service ADR](https://github.com/stellhub/stellpulsar-service/blob/main/docs/ADR.md)
- [Distributed Quota Consistency Design](https://github.com/stellhub/stellpulsar-service/blob/main/docs/distributed-quota-consistency.md)
