# StellPulsar Java SDK

`stellpulsar-java-sdk` is the Java client SDK for [`stellhub/stellpulsar-service`](https://github.com/stellhub/stellpulsar-service), the weakly consistent distributed rate limiting server for traffic governance, quota checks, and low-latency token bucket decisions.

## Positioning

This repository provides the Java client implementation for applications, gateways, platform services, and middleware components that need to consume StellPulsar rate limiting capabilities.

It does not implement distributed quota coordination locally. The SDK delegates limit decisions to `stellpulsar-service` and keeps Java applications aligned with the central StellPulsar rate limiting control surface.

## Capabilities

- Rate limit check requests.
- Per-key limit, window, and cost parameters.
- API key based authentication header support.
- Standard Java `HttpClient` transport without third-party runtime dependencies.
- Timeout configuration for connection and request execution.

## Current Status

| Item | Value |
| --- | --- |
| Stability | Early development |
| Language | Java |
| Minimum Java version | 17 |
| Transport | `java.net.http.HttpClient` |
| Target service | `stellpulsar-service` |
| Maintainer | StellHub |

## Installation

The artifact coordinates are reserved for future publishing:

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

import io.github.stellhub.stellpulsar.client.StellpulsarClient;
import io.github.stellhub.stellpulsar.client.StellpulsarClientOptions;
import io.github.stellhub.stellpulsar.client.StellpulsarHttpClient;
import io.github.stellhub.stellpulsar.client.model.ApiResponse;
import io.github.stellhub.stellpulsar.client.model.LimitCheckRequest;
import java.net.URI;

public class StellpulsarExample {

    public static void main(String[] args) {
        StellpulsarClientOptions options = StellpulsarClientOptions.builder()
                .endpoint(URI.create("http://localhost:8080"))
                .apiKey("local-dev-api-key")
                .build();

        try (StellpulsarClient client = new StellpulsarHttpClient(options)) {
            LimitCheckRequest request = new LimitCheckRequest(
                    "tenant-a:/api/orders",
                    100,
                    60,
                    1
            );

            ApiResponse response = client.check(request);
            System.out.println(response.body());
        }
    }
}
```

## API Surface

| Method | Responsibility |
| --- | --- |
| `check(LimitCheckRequest request)` | Request a rate limit decision from StellPulsar |

## Development

Run verification:

```bash
mvn test
```

## Repository Scope

This SDK intentionally keeps the first version small. Future releases can add:

- Strongly typed limit decision models.
- OpenAPI generated DTOs when the service contract is stable.
- Integration tests against local `stellpulsar-service`.
- Retry and fail-open/fail-closed helpers.
- Observability hooks for client-side metrics and tracing.

## License

The license will be defined before the first stable release.
