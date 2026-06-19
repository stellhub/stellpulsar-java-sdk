# StellPulsar Java SDK

[English](README.md)

`stellpulsar-java-sdk` 是 [`stellhub/stellpulsar-service`](https://github.com/stellhub/stellpulsar-service) 的官方 Java 客户端 SDK。它为 Java 应用和框架适配层提供不依赖 Spring 的分布式配额客户端，用于基于 StellPulsar 执行分布式限流规则判定。

SDK 通过 `stellorbit-java-sdk` 消费限流规则，过滤出需要由 StellPulsar 执行的分布式限流规则，发现当前 StellPulsar 拓扑，通过 gRPC 将配额请求路由到正确的 shard owner，并通过 `tryAcquire` API 返回框架无关的 `RateLimitResult`。

## 项目状态

| 项目 | 值 |
| --- | --- |
| 稳定性 | 早期开发 |
| 语言 | Java |
| 最低 Java 版本 | 25 |
| 传输协议 | gRPC |
| 规则来源 | `stellorbit-java-sdk` |
| 目标服务 | `stellpulsar-service` |
| Spring 依赖 | 无 |
| Resilience4j 依赖 | 核心 SDK 不引入 |

## 设计目标

- 保持核心 SDK 与 Spring、Servlet、WebFlux、Gateway 和框架级拦截器解耦。
- 统一通过 `stellorbit-java-sdk` 获取规则，不直接调用规则管理 API。
- 使用 topology revision 和 `rendezvous_hash_v1`，确保相同的 `applicationCode + ruleId + quotaKey` 路由到同一个 StellPulsar owner。
- 在配额请求中携带 rule revision、checksum、topology revision、target instance 和 shard hash，使客户端和服务端能够识别一致性差异。
- 暴露足够小且稳定的 Java API，使 `stellflux`、Resilience4j adapter 或其他框架层能够独立完成集成，而不把这些依赖耦合进当前仓库。

## SDK 负责什么

- 从 StellOrbit 限流规则 Provider 中过滤分布式限流规则。
- 通过 StellPulsar gRPC discovery API 维护拓扑发现。
- 使用 `rendezvous_hash_v1` 计算 owner 路由。
- 通过 gRPC 调用 `AcquireQuota`，并将服务端判定映射为 Java 结果模型。
- 处理 `SERVER_RULE_LAG`、`RULE_STALE`、`RULE_CONFLICT`、`NOT_OWNER`、`SHARD_MIGRATING` 等常见一致性响应。
- 通过 `FailPolicy` 提供 fail-open 和 fail-closed 结果语义。

## SDK 不负责什么

- 不在客户端本地实现分布式限流算法。
- 不提供 Spring 自动装配、拦截器、Filter、注解或 AOP 集成。
- 不依赖 Resilience4j。Resilience4j 应在 `stellflux` 或独立的可选 adapter 模块中接入。
- 不绕过 `stellorbit-java-sdk` 直接从 StellOrbit 或 StellNula 读取治理规则。

## 安装

制品发布后，可在项目中添加以下依赖：

```xml
<dependency>
    <groupId>io.github.stellhub</groupId>
    <artifactId>stellpulsar-java-sdk</artifactId>
    <version>${stellpulsar-java-sdk.version}</version>
</dependency>
```

## 快速开始

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

## 公共 API

| 类型 | 职责 |
| --- | --- |
| `StellpulsarClient.tryAcquire(RateLimitRequest)` | 尝试获取远端分布式配额。 |
| `StellpulsarClient.isLimited(RateLimitRequest)` | 便捷布尔判断，适合只关心是否拒绝的框架集成。 |
| `RateLimitRequest` | 框架无关的请求模型，包含 application、resource、method、tenant、quota key、cost 和 attributes。 |
| `RateLimitResult` | 判定结果模型，包含 `permitted`、`limited`、`fallback`、`decision`、`retryAfterMs` 和诊断字段。 |
| `DistributedRateLimitRuleProvider` | SDK 内部规则 SPI。 |
| `StellorbitDistributedRateLimitRuleProvider` | 将 `stellorbit-java-sdk` 的限流规则适配为 StellPulsar 分布式配额规则。 |
| `GrpcStellpulsarTransport` | topology discovery 和 quota acquisition 的 gRPC 实现。 |

## 判定语义

`tryAcquire` 可能触发远端配额扣减，因此应被视为一次 acquire 操作，而不是只读 check。

| 结果 | 含义 |
| --- | --- |
| `permitted=true` | 调用方可以继续执行业务逻辑。 |
| `limited=true` | 调用方应拒绝请求，通常映射为 HTTP 429 或框架级限流异常。 |
| `fallback=true` | SDK 未能完成正常远端配额判定，结果来自 fail policy。 |
| `retryAfterMs > 0` | 调用方可以用该值填充重试响应头或框架级退避元数据。 |

## 框架集成

核心 SDK 有意避免引入框架依赖。`stellflux` 这类框架层应负责：

- Spring bean 生命周期和配置绑定。
- HTTP、RPC、Gateway 或方法级拦截。
- 将 `RateLimitResult` 映射为 HTTP 429、异常、fallback 响应或指标。
- 可选的 Resilience4j 集成和事件转换。

这种边界让 SDK 可以同时用于普通 Java 应用、中间件组件和 Spring 适配层，而不会强制绑定某一种执行模型。

## 开发

运行测试：

```bash
mvn test
```

## 文档

- [SDK ADR](docs/ADR.md)
- [StellPulsar Service ADR](https://github.com/stellhub/stellpulsar-service/blob/main/docs/ADR.md)
- [分布式配额一致性设计](https://github.com/stellhub/stellpulsar-service/blob/main/docs/distributed-quota-consistency.md)
