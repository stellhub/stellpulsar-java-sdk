package io.github.stellhub.stellpulsar.client.transport.grpc;

import io.github.stellhub.stellpulsar.client.model.RateLimitDecision;
import io.github.stellhub.stellpulsar.client.quota.AcquireQuotaCommand;
import io.github.stellhub.stellpulsar.client.quota.QuotaGateway;
import io.github.stellhub.stellpulsar.client.quota.QuotaGatewayResponse;
import io.github.stellhub.stellpulsar.client.topology.PulsarInstance;
import io.github.stellhub.stellpulsar.client.topology.TopologyDiscoveryClient;
import io.github.stellhub.stellpulsar.client.topology.TopologyDiscoveryRequest;
import io.github.stellhub.stellpulsar.client.topology.TopologySnapshot;
import io.github.stellhub.stellpulsar.proto.v1.AcquireQuotaRequest;
import io.github.stellhub.stellpulsar.proto.v1.AcquireQuotaResponse;
import io.github.stellhub.stellpulsar.proto.v1.ListInstancesRequest;
import io.github.stellhub.stellpulsar.proto.v1.ListInstancesResponse;
import io.github.stellhub.stellpulsar.proto.v1.QuotaDecision;
import io.github.stellhub.stellpulsar.proto.v1.StellPulsarDiscoveryServiceGrpc;
import io.github.stellhub.stellpulsar.proto.v1.StellPulsarRuntimeServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class GrpcStellpulsarTransport implements TopologyDiscoveryClient, QuotaGateway, AutoCloseable {

    static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    static final Metadata.Key<String> STELLPULSAR_TOKEN_KEY =
            Metadata.Key.of("x-stellpulsar-token", Metadata.ASCII_STRING_MARSHALLER);

    private final String discoveryHost;
    private final int discoveryPort;
    private final boolean plaintext;
    private final String apiToken;
    private final Duration deadline;
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    private GrpcStellpulsarTransport(Builder builder) {
        this.discoveryHost = requireText(builder.discoveryHost, "discoveryHost");
        this.discoveryPort = requirePort(builder.discoveryPort);
        this.plaintext = builder.plaintext;
        this.apiToken = builder.apiToken == null ? "" : builder.apiToken.trim();
        this.deadline = Objects.requireNonNull(builder.deadline, "deadline must not be null");
    }

    /**
     * 创建 gRPC transport 构造器。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 通过 gRPC ListInstances 查询服务端拓扑。
     */
    @Override
    public TopologySnapshot listInstances(TopologyDiscoveryRequest request) {
        ListInstancesRequest grpcRequest = ListInstancesRequest.newBuilder()
                .setNamespace(request.namespace())
                .setApplicationCode(request.applicationCode())
                .setClientId(request.clientId())
                .addAllSupportedProtocolVersions(request.supportedProtocolVersions())
                .putAllLabels(request.labels())
                .build();
        ListInstancesResponse response = withDeadline(
                StellPulsarDiscoveryServiceGrpc.newBlockingStub(channel(discoveryHost, discoveryPort)))
                .listInstances(grpcRequest);
        return new TopologySnapshot(
                response.getProtocolVersion(),
                response.getInstanceRevision(),
                response.getExpiresAtUnixMs(),
                response.getHashAlgorithm(),
                response.getInstancesList().stream()
                        .map(GrpcStellpulsarTransport::toDomainInstance)
                        .toList());
    }

    /**
     * 通过 gRPC AcquireQuota 申请配额。
     */
    @Override
    public QuotaGatewayResponse acquire(AcquireQuotaCommand command) {
        PulsarInstance owner = command.owner().instance();
        AcquireQuotaRequest grpcRequest = AcquireQuotaRequest.newBuilder()
                .setRequestId(command.requestId())
                .setApplicationCode(command.applicationCode())
                .setClientId(command.clientId())
                .setRuleId(command.rule().ruleId())
                .setQuotaKey(command.quotaKey())
                .setCost(command.cost())
                .setRuleRevision(command.rule().revision())
                .setRuleChecksum(command.rule().checksum())
                .putAllAttributes(command.attributes())
                .setTopologyRevision(command.topologyRevision())
                .setTargetInstanceId(command.targetInstanceId())
                .setShardHash(command.shardHash())
                .build();
        AcquireQuotaResponse response = withDeadline(
                StellPulsarRuntimeServiceGrpc.newBlockingStub(channel(owner.host(), owner.port())))
                .acquireQuota(grpcRequest);
        return new QuotaGatewayResponse(
                response.getRequestId(),
                toDecision(response.getDecision()),
                response.getRuleId(),
                response.getRuleRevision(),
                response.getRuleChecksum(),
                response.getRemaining(),
                response.getResetAtUnixMs(),
                response.getRetryAfterMs(),
                response.getReason(),
                response.getTopologyRevision(),
                response.hasOwnerInstance() ? toDomainInstance(response.getOwnerInstance()) : null,
                response.getHashAlgorithm());
    }

    /**
     * 关闭所有 gRPC channel。
     */
    @Override
    public void close() {
        channels.values().forEach(channel -> channel.shutdownNow());
        channels.clear();
    }

    private ManagedChannel channel(String host, int port) {
        String key = host + ":" + port;
        return channels.computeIfAbsent(key, ignored -> {
            ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port);
            if (plaintext) {
                builder.usePlaintext();
            }
            return builder.build();
        });
    }

    private <T extends AbstractStub<T>> T withDeadline(T stub) {
        T resolved = stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS);
        if (apiToken.isBlank()) {
            return resolved;
        }
        Metadata metadata = authorizationMetadata(apiToken);
        return resolved.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    static Metadata authorizationMetadata(String apiToken) {
        Metadata metadata = new Metadata();
        String token = apiToken == null ? "" : apiToken.trim();
        if (!token.isBlank()) {
            metadata.put(STELLPULSAR_TOKEN_KEY, token);
            metadata.put(AUTHORIZATION_KEY, bearerToken(token));
        }
        return metadata;
    }

    private static String bearerToken(String token) {
        return token.regionMatches(true, 0, "bearer ", 0, "bearer ".length()) ? token : "Bearer " + token;
    }

    private static PulsarInstance toDomainInstance(
            io.github.stellhub.stellpulsar.proto.v1.PulsarInstance instance) {
        return new PulsarInstance(
                instance.getInstanceId(),
                instance.getHost(),
                instance.getPort(),
                instance.getPriority(),
                instance.getWeight(),
                instance.getZone(),
                instance.getVersion(),
                instance.getRuleRevision(),
                instance.getState(),
                instance.getMetadataMap());
    }

    private static RateLimitDecision toDecision(QuotaDecision decision) {
        return switch (decision) {
            case QUOTA_DECISION_ALLOWED -> RateLimitDecision.ALLOWED;
            case QUOTA_DECISION_DENIED -> RateLimitDecision.DENIED;
            case QUOTA_DECISION_RULE_STALE -> RateLimitDecision.RULE_STALE;
            case QUOTA_DECISION_SERVER_RULE_LAG -> RateLimitDecision.SERVER_RULE_LAG;
            case QUOTA_DECISION_RULE_CONFLICT -> RateLimitDecision.RULE_CONFLICT;
            case QUOTA_DECISION_RULE_NOT_FOUND -> RateLimitDecision.RULE_NOT_FOUND;
            case QUOTA_DECISION_INVALID_REQUEST -> RateLimitDecision.INVALID_REQUEST;
            case QUOTA_DECISION_NOT_OWNER -> RateLimitDecision.NOT_OWNER;
            case QUOTA_DECISION_SHARD_MIGRATING -> RateLimitDecision.SHARD_MIGRATING;
            default -> RateLimitDecision.UNSPECIFIED;
        };
    }

    public static final class Builder {

        private String discoveryHost;
        private int discoveryPort;
        private boolean plaintext = true;
        private String apiToken;
        private Duration deadline = Duration.ofSeconds(3);

        private Builder() {
        }

        /**
         * 设置 discovery gRPC 地址。
         */
        public Builder discoveryAddress(String host, int port) {
            this.discoveryHost = host;
            this.discoveryPort = port;
            return this;
        }

        /**
         * 设置是否使用明文 gRPC。
         */
        public Builder plaintext(boolean plaintext) {
            this.plaintext = plaintext;
            return this;
        }

        /**
         * 设置 API token。
         */
        public Builder apiToken(String apiToken) {
            this.apiToken = apiToken;
            return this;
        }

        /**
         * 设置 gRPC deadline。
         */
        public Builder deadline(Duration deadline) {
            this.deadline = Objects.requireNonNull(deadline, "deadline must not be null");
            return this;
        }

        /**
         * 构建 gRPC transport。
         */
        public GrpcStellpulsarTransport build() {
            return new GrpcStellpulsarTransport(this);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static int requirePort(int port) {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        return port;
    }
}
