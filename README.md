# StellPulsar Java SDK

[中文文档](README_CN.md)

`stellpulsar-java-sdk` is the official Java client SDK for [`stellhub/stellpulsar-service`](https://github.com/stellhub/stellpulsar-service). It provides a Spring-free distributed quota client for Java applications and framework adapters that need to evaluate distributed rate limiting rules with StellPulsar.

The SDK consumes rate limit rules from `stellorbit-java-sdk`, filters rules that must be evaluated by StellPulsar, discovers the active StellPulsar topology, routes quota requests to the correct shard owner over gRPC, and returns a framework-neutral `RateLimitResult` through the `tryAcquire` API.

## Project Status

| Item | Value |
| --- | --- |
| Stability | Early development |
| Language | Java |
| Minimum Java version | 25 |
| Transport | gRPC |
| Rule source | `stellorbit-java-sdk` |
| Target service | `stellpulsar-service` |
| Spring dependency | None |
| Resilience4j dependency | None in core SDK |

## Design Goals

- Keep the core SDK independent from Spring, Servlet, WebFlux, Gateway, and framework-specific interceptors.
- Use `stellorbit-java-sdk` as the only rule data source instead of directly calling rule management APIs.
- Route each `applicationCode + ruleId + quotaKey` to the same StellPulsar owner by using topology revision and `rendezvous_hash_v1`.
- Carry rule revision, checksum, topology revision, target instance, and shard hash in quota requests so client and server can detect consistency gaps.
- Expose a small Java API that can be integrated by `stellflux`, Resilience4j adapters, or other framework layers without coupling those dependencies into this repository.

## What This SDK Does

- Filters distributed rate limit rules from StellOrbit rate limit rule providers.
- Maintains topology discovery through StellPulsar gRPC discovery APIs.
- Computes owner routing with `rendezvous_hash_v1`.
- Calls `AcquireQuota` over gRPC and maps service decisions to Java result models.
- Handles common consistency responses such as `SERVER_RULE_LAG`, `RULE_STALE`, `RULE_CONFLICT`, `NOT_OWNER`, and `SHARD_MIGRATING`.
- Provides fail-open and fail-closed result semantics through `FailPolicy`.

## What This SDK Does Not Do

- It does not implement a local distributed rate limiting algorithm.
- It does not provide Spring auto-configuration, interceptors, filters, annotations, or AOP integration.
- It does not depend on Resilience4j. Resilience4j should be integrated in `stellflux` or a separate optional adapter module.
- It does not bypass `stellorbit-java-sdk` to read governance rules directly from StellOrbit or StellNula.

## Installation

After the artifact is published, add the SDK dependency to your project:

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

## Public API

| Type | Responsibility |
| --- | --- |
| `StellpulsarClient.tryAcquire(RateLimitRequest)` | Attempts to acquire remote distributed quota. |
| `StellpulsarClient.isLimited(RateLimitRequest)` | Convenience method for integrations that only need a boolean rejection signal. |
| `RateLimitRequest` | Framework-neutral request model containing application, resource, method, tenant, quota key, cost, and attributes. |
| `RateLimitResult` | Decision model containing `permitted`, `limited`, `fallback`, `decision`, `retryAfterMs`, and diagnostic fields. |
| `DistributedRateLimitRuleProvider` | SDK rule SPI used by the quota client. |
| `StellorbitDistributedRateLimitRuleProvider` | Adapter from `stellorbit-java-sdk` rate limit rules to StellPulsar distributed quota rules. |
| `GrpcStellpulsarTransport` | gRPC implementation for topology discovery and quota acquisition. |

## Decision Semantics

`tryAcquire` may perform a remote quota mutation. It should be treated as an acquire operation rather than a read-only check.

| Result | Meaning |
| --- | --- |
| `permitted=true` | The caller may continue business execution. |
| `limited=true` | The caller should reject the request, usually as HTTP 429 or a framework-specific rate limit exception. |
| `fallback=true` | The result was produced by fail policy because the SDK could not complete a normal remote quota decision. |
| `retryAfterMs > 0` | The caller may use this value to populate retry headers or framework-specific backoff metadata. |

## Framework Integration

The core SDK intentionally avoids framework dependencies. A framework layer such as `stellflux` should own:

- Spring bean lifecycle and configuration binding.
- HTTP, RPC, gateway, or method interception.
- Mapping `RateLimitResult` to HTTP 429, exceptions, fallback responses, or metrics.
- Optional Resilience4j integration and event conversion.

This keeps the SDK usable in plain Java applications, middleware components, and Spring-based adapters without forcing a single execution model.

## Development

Run the test suite:

```bash
mvn test
```

## Documentation

- [SDK ADR](docs/ADR.md)
- [StellPulsar Service ADR](https://github.com/stellhub/stellpulsar-service/blob/main/docs/ADR.md)
- [Distributed Quota Consistency Design](https://github.com/stellhub/stellpulsar-service/blob/main/docs/distributed-quota-consistency.md)
