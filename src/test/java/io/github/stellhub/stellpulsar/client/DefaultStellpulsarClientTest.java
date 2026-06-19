package io.github.stellhub.stellpulsar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.stellhub.stellpulsar.client.model.FailPolicy;
import io.github.stellhub.stellpulsar.client.model.RateLimitDecision;
import io.github.stellhub.stellpulsar.client.model.RateLimitRequest;
import io.github.stellhub.stellpulsar.client.model.RateLimitResult;
import io.github.stellhub.stellpulsar.client.quota.QuotaGateway;
import io.github.stellhub.stellpulsar.client.quota.QuotaGatewayResponse;
import io.github.stellhub.stellpulsar.client.rule.DistributedRateLimitRule;
import io.github.stellhub.stellpulsar.client.rule.DistributedRateLimitRuleProvider;
import io.github.stellhub.stellpulsar.client.rule.RefreshableDistributedRateLimitRuleProvider;
import io.github.stellhub.stellpulsar.client.rule.StaticDistributedRateLimitRuleProvider;
import io.github.stellhub.stellpulsar.client.topology.PulsarInstance;
import io.github.stellhub.stellpulsar.client.topology.RendezvousHashOwnerSelector;
import io.github.stellhub.stellpulsar.client.topology.SelectedOwner;
import io.github.stellhub.stellpulsar.client.topology.TopologyManager;
import io.github.stellhub.stellpulsar.client.topology.TopologySnapshot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DefaultStellpulsarClientTest {

    @Test
    void tryAcquirePermitsWhenNoDistributedRuleMatches() {
        StellpulsarClient client = client(List.of(), command -> {
            throw new AssertionError("quota gateway should not be called");
        });

        RateLimitResult result = client.tryAcquire(request());

        assertTrue(result.permitted());
        assertEquals(RateLimitDecision.NO_MATCHING_RULE, result.decision());
    }

    @Test
    void tryAcquireRejectsWhenRemoteQuotaIsDenied() {
        DistributedRateLimitRule rule = rule(FailPolicy.FAIL_OPEN);
        StellpulsarClient client = client(List.of(rule), command -> QuotaGatewayResponse.denied(
                command.requestId(),
                command.rule().ruleId(),
                command.rule().revision(),
                command.rule().checksum(),
                1000));

        RateLimitResult result = client.tryAcquire(request());

        assertFalse(result.permitted());
        assertTrue(result.limited());
        assertEquals(RateLimitDecision.DENIED, result.decision());
        assertEquals(1000, result.retryAfterMs());
    }

    @Test
    void tryAcquireFallsClosedWhenGatewayFailsAndRuleRequiresFailClosed() {
        DistributedRateLimitRule rule = rule(FailPolicy.FAIL_CLOSED);
        StellpulsarClient client = client(List.of(rule), command -> {
            throw new IllegalStateException("boom");
        });

        RateLimitResult result = client.tryAcquire(request());

        assertFalse(result.permitted());
        assertTrue(result.fallback());
        assertEquals(RateLimitDecision.FALLBACK_DENIED, result.decision());
    }

    @Test
    void tryAcquireRetriesServerLagThenPermits() {
        DistributedRateLimitRule rule = rule(FailPolicy.FAIL_OPEN);
        List<RateLimitDecision> calls = new ArrayList<>();
        StellpulsarClient client = client(List.of(rule), command -> {
            calls.add(calls.isEmpty() ? RateLimitDecision.SERVER_RULE_LAG : RateLimitDecision.ALLOWED);
            if (calls.size() == 1) {
                return new QuotaGatewayResponse(
                        command.requestId(),
                        RateLimitDecision.SERVER_RULE_LAG,
                        command.rule().ruleId(),
                        command.rule().revision(),
                        command.rule().checksum(),
                        -1,
                        0,
                        0,
                        "server_rule_lag",
                        command.topologyRevision(),
                        null,
                        "");
            }
            return QuotaGatewayResponse.allowed(
                    command.requestId(),
                    command.rule().ruleId(),
                    command.rule().revision(),
                    command.rule().checksum());
        });

        RateLimitResult result = client.tryAcquire(request());

        assertTrue(result.permitted());
        assertEquals(2, calls.size());
    }

    @Test
    void tryAcquireUsesRedirectOwnerAfterNotOwner() {
        DistributedRateLimitRule rule = rule(FailPolicy.FAIL_OPEN);
        PulsarInstance redirected = new PulsarInstance(
                "pulsar-b",
                "127.0.0.2",
                9090,
                100,
                100,
                "",
                "",
                "",
                "UP",
                Map.of());
        List<String> targetInstances = new ArrayList<>();
        StellpulsarClient client = client(List.of(rule), command -> {
            targetInstances.add(command.targetInstanceId());
            if (targetInstances.size() == 1) {
                return new QuotaGatewayResponse(
                        command.requestId(),
                        RateLimitDecision.NOT_OWNER,
                        command.rule().ruleId(),
                        command.rule().revision(),
                        command.rule().checksum(),
                        -1,
                        0,
                        0,
                        "not_owner",
                        command.topologyRevision(),
                        redirected,
                        TopologySnapshot.RENDEZVOUS_HASH_V1);
            }
            return QuotaGatewayResponse.allowed(
                    command.requestId(),
                    command.rule().ruleId(),
                    command.rule().revision(),
                    command.rule().checksum());
        });

        RateLimitResult result = client.tryAcquire(request());

        assertTrue(result.permitted());
        assertEquals("pulsar-b", targetInstances.get(1));
    }

    @Test
    void tryAcquireRefreshesTopologyAndReselectsWhenNotOwnerHasNoRedirect() {
        DistributedRateLimitRule rule = rule(FailPolicy.FAIL_OPEN);
        RefreshingTopologyManager topologyManager = new RefreshingTopologyManager();
        List<String> targetInstances = new ArrayList<>();
        StellpulsarClient client = client(
                new StaticDistributedRateLimitRuleProvider(List.of(rule)),
                topologyManager,
                command -> {
                    targetInstances.add(command.targetInstanceId());
                    if (targetInstances.size() == 1) {
                        return new QuotaGatewayResponse(
                                command.requestId(),
                                RateLimitDecision.NOT_OWNER,
                                command.rule().ruleId(),
                                command.rule().revision(),
                                command.rule().checksum(),
                                -1,
                                0,
                                0,
                                "not_owner",
                                command.topologyRevision(),
                                null,
                                TopologySnapshot.RENDEZVOUS_HASH_V1);
                    }
                    return QuotaGatewayResponse.allowed(
                            command.requestId(),
                            command.rule().ruleId(),
                            command.rule().revision(),
                            command.rule().checksum());
                });

        RateLimitResult result = client.tryAcquire(request());

        assertTrue(result.permitted());
        assertEquals("pulsar-a", targetInstances.get(0));
        assertEquals("pulsar-b", targetInstances.get(1));
        assertTrue(topologyManager.refreshed.get());
    }

    @Test
    void tryAcquireRefreshesTopologyWhenNotOwnerReturnsNewRevision() {
        DistributedRateLimitRule rule = rule(FailPolicy.FAIL_OPEN);
        RevisionChangingTopologyManager topologyManager = new RevisionChangingTopologyManager();
        PulsarInstance redirected = new PulsarInstance(
                "pulsar-b",
                "127.0.0.2",
                9090,
                100,
                100,
                "",
                "",
                "",
                "UP",
                Map.of());
        List<String> targetInstances = new ArrayList<>();
        List<String> topologyRevisions = new ArrayList<>();
        StellpulsarClient client = client(
                new StaticDistributedRateLimitRuleProvider(List.of(rule)),
                topologyManager,
                command -> {
                    targetInstances.add(command.targetInstanceId());
                    topologyRevisions.add(command.topologyRevision());
                    if (targetInstances.size() == 1) {
                        return new QuotaGatewayResponse(
                                command.requestId(),
                                RateLimitDecision.NOT_OWNER,
                                command.rule().ruleId(),
                                command.rule().revision(),
                                command.rule().checksum(),
                                -1,
                                0,
                                0,
                                "not_owner",
                                "rev-2",
                                redirected,
                                TopologySnapshot.RENDEZVOUS_HASH_V1);
                    }
                    return QuotaGatewayResponse.allowed(
                            command.requestId(),
                            command.rule().ruleId(),
                            command.rule().revision(),
                            command.rule().checksum());
                });

        RateLimitResult result = client.tryAcquire(request());

        assertTrue(result.permitted());
        assertEquals(List.of("pulsar-a", "pulsar-c"), targetInstances);
        assertEquals(List.of("rev-1", "rev-2"), topologyRevisions);
        assertTrue(topologyManager.refreshed.get());
    }

    @Test
    void tryAcquireReloadsRuleAfterRuleStale() {
        RefreshingRuleProvider ruleProvider = new RefreshingRuleProvider();
        List<String> revisions = new ArrayList<>();
        StellpulsarClient client = client(ruleProvider, topologyManager(), command -> {
            revisions.add(command.rule().revision());
            if (revisions.size() == 1) {
                return new QuotaGatewayResponse(
                        command.requestId(),
                        RateLimitDecision.RULE_STALE,
                        command.rule().ruleId(),
                        "101",
                        "def",
                        -1,
                        0,
                        0,
                        "rule_stale",
                        command.topologyRevision(),
                        null,
                        "");
            }
            return QuotaGatewayResponse.allowed(
                    command.requestId(),
                    command.rule().ruleId(),
                    command.rule().revision(),
                    command.rule().checksum());
        });

        RateLimitResult result = client.tryAcquire(request());

        assertTrue(result.permitted());
        assertEquals(List.of("100", "101"), revisions);
    }

    @Test
    void tryAcquireUsesRuleCostWhenRequestCostIsUnspecified() {
        DistributedRateLimitRule rule = rule(FailPolicy.FAIL_OPEN, "100", "abc", 7);
        List<Long> costs = new ArrayList<>();
        StellpulsarClient client = client(List.of(rule), command -> {
            costs.add(command.cost());
            return QuotaGatewayResponse.allowed(
                    command.requestId(),
                    command.rule().ruleId(),
                    command.rule().revision(),
                    command.rule().checksum());
        });

        RateLimitResult result = client.tryAcquire(request());

        assertTrue(result.permitted());
        assertEquals(List.of(7L), costs);
    }

    @Test
    void listenerFailureDoesNotChangeQuotaResult() {
        DistributedRateLimitRule rule = rule(FailPolicy.FAIL_OPEN);
        StellpulsarClient client = new DefaultStellpulsarClient(StellpulsarClientOptions.builder()
                .applicationCode("order-service")
                .clientId("client-a")
                .ruleProvider(new StaticDistributedRateLimitRuleProvider(List.of(rule)))
                .topologyManager(topologyManager())
                .quotaGateway(command -> QuotaGatewayResponse.allowed(
                        command.requestId(),
                        command.rule().ruleId(),
                        command.rule().revision(),
                        command.rule().checksum()))
                .eventListener(event -> {
                    throw new IllegalStateException("listener failed");
                })
                .retryDelay(Duration.ZERO)
                .build());

        RateLimitResult result = client.tryAcquire(request());

        assertTrue(result.permitted());
    }

    private static StellpulsarClient client(List<DistributedRateLimitRule> rules, QuotaGateway quotaGateway) {
        return client(new StaticDistributedRateLimitRuleProvider(rules), topologyManager(), quotaGateway);
    }

    private static StellpulsarClient client(
            DistributedRateLimitRuleProvider ruleProvider,
            TopologyManager topologyManager,
            QuotaGateway quotaGateway) {
        return new DefaultStellpulsarClient(StellpulsarClientOptions.builder()
                .applicationCode("order-service")
                .clientId("client-a")
                .ruleProvider(ruleProvider)
                .topologyManager(topologyManager)
                .quotaGateway(quotaGateway)
                .retryDelay(Duration.ZERO)
                .maxAcquireAttempts(3)
                .build());
    }

    private static TopologyManager topologyManager() {
        PulsarInstance instance = new PulsarInstance(
                "pulsar-a",
                "127.0.0.1",
                9090,
                100,
                100,
                "",
                "",
                "",
                "UP",
                Map.of());
        TopologySnapshot snapshot = TopologySnapshot.single(instance);
        RendezvousHashOwnerSelector selector = new RendezvousHashOwnerSelector();
        return new TopologyManager() {
            @Override
            public SelectedOwner ownerOf(String shardKey) {
                return selector.select(snapshot, shardKey);
            }

            @Override
            public TopologySnapshot refresh() {
                return snapshot;
            }
        };
    }

    private static RateLimitRequest request() {
        return RateLimitRequest.builder()
                .requestId("req-1")
                .applicationCode("order-service")
                .targetService("order-service")
                .resource("/orders")
                .method("POST")
                .tenantId("tenant-a")
                .quotaKey("tenant-a:/orders")
                .build();
    }

    private static DistributedRateLimitRule rule(FailPolicy failPolicy) {
        return rule(failPolicy, "100", "abc", 1);
    }

    private static DistributedRateLimitRule rule(FailPolicy failPolicy, String revision, String checksum, long cost) {
        return DistributedRateLimitRule.builder()
                .applicationCode("order-service")
                .ruleId("rule-a")
                .ruleName("orders")
                .revision(revision)
                .checksum(checksum)
                .schemaVersion("v1")
                .algorithm("fixed_window")
                .quota(100)
                .windowSeconds(60)
                .dimensions(List.of("tenantId", "resource"))
                .cost(cost)
                .failPolicy(failPolicy)
                .build();
    }

    private static final class RefreshingRuleProvider implements RefreshableDistributedRateLimitRuleProvider {

        private boolean refreshed;

        @Override
        public List<DistributedRateLimitRule> find(RateLimitRequest request) {
            return List.of(refreshed
                    ? rule(FailPolicy.FAIL_OPEN, "101", "def", 1)
                    : rule(FailPolicy.FAIL_OPEN, "100", "abc", 1));
        }

        @Override
        public void refresh() {
            refreshed = true;
        }
    }

    private static final class RefreshingTopologyManager implements TopologyManager {

        private final AtomicBoolean refreshed = new AtomicBoolean();
        private final RendezvousHashOwnerSelector selector = new RendezvousHashOwnerSelector();

        @Override
        public SelectedOwner ownerOf(String shardKey) {
            return selector.select(snapshot(refreshed.get() ? "pulsar-b" : "pulsar-a"), shardKey);
        }

        @Override
        public TopologySnapshot refresh() {
            refreshed.set(true);
            return snapshot("pulsar-b");
        }

        private static TopologySnapshot snapshot(String instanceId) {
            return TopologySnapshot.single(new PulsarInstance(
                    instanceId,
                    "127.0.0.1",
                    9090,
                    100,
                    100,
                    "",
                    "",
                    "",
                    "UP",
                    Map.of()));
        }
    }

    private static final class RevisionChangingTopologyManager implements TopologyManager {

        private final AtomicBoolean refreshed = new AtomicBoolean();
        private final RendezvousHashOwnerSelector selector = new RendezvousHashOwnerSelector();

        @Override
        public SelectedOwner ownerOf(String shardKey) {
            return refreshed.get()
                    ? selector.select(snapshot("rev-2", "pulsar-c"), shardKey)
                    : selector.select(snapshot("rev-1", "pulsar-a"), shardKey);
        }

        @Override
        public TopologySnapshot refresh() {
            refreshed.set(true);
            return snapshot("rev-2", "pulsar-c");
        }

        private static TopologySnapshot snapshot(String topologyRevision, String instanceId) {
            return new TopologySnapshot(
                    "stellpulsar.v1",
                    topologyRevision,
                    Long.MAX_VALUE,
                    TopologySnapshot.RENDEZVOUS_HASH_V1,
                    List.of(new PulsarInstance(
                            instanceId,
                            "127.0.0.1",
                            9090,
                            100,
                            100,
                            "",
                            "",
                            "",
                            "UP",
                            Map.of())));
        }
    }
}
