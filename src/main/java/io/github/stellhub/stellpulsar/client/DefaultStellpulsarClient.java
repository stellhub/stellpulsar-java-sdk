package io.github.stellhub.stellpulsar.client;

import io.github.stellhub.stellpulsar.client.event.StellpulsarEvent;
import io.github.stellhub.stellpulsar.client.event.StellpulsarEventType;
import io.github.stellhub.stellpulsar.client.model.FailPolicy;
import io.github.stellhub.stellpulsar.client.model.RateLimitDecision;
import io.github.stellhub.stellpulsar.client.model.RateLimitRequest;
import io.github.stellhub.stellpulsar.client.model.RateLimitResult;
import io.github.stellhub.stellpulsar.client.quota.AcquireQuotaCommand;
import io.github.stellhub.stellpulsar.client.quota.QuotaGatewayResponse;
import io.github.stellhub.stellpulsar.client.rule.DistributedRateLimitRule;
import io.github.stellhub.stellpulsar.client.rule.RefreshableDistributedRateLimitRuleProvider;
import io.github.stellhub.stellpulsar.client.topology.PulsarInstance;
import io.github.stellhub.stellpulsar.client.topology.RendezvousHashOwnerSelector;
import io.github.stellhub.stellpulsar.client.topology.SelectedOwner;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultStellpulsarClient implements StellpulsarClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultStellpulsarClient.class);

    private final StellpulsarClientOptions options;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultStellpulsarClient(StellpulsarClientOptions options) {
        this.options = Objects.requireNonNull(options, "options must not be null");
    }

    /**
     * 启动客户端。
     */
    @Override
    public void start() {
        ensureOpen();
        started.compareAndSet(false, true);
    }

    /**
     * 尝试获取分布式限流配额。
     */
    @Override
    public RateLimitResult tryAcquire(RateLimitRequest request) {
        ensureOpen();
        if (!started.get()) {
            start();
        }
        Objects.requireNonNull(request, "request must not be null");
        List<DistributedRateLimitRule> rules;
        try {
            rules = options.ruleProvider().find(request);
        } catch (RuntimeException e) {
            log.warn("failed to load distributed rate limit rules, requestId={}", request.requestId(), e);
            RateLimitResult result = RateLimitResult.fallback(
                    options.defaultFailPolicy(),
                    RateLimitDecision.CLIENT_ERROR,
                    "",
                    "",
                    "",
                    "failed_to_load_rules",
                    e.getClass().getSimpleName());
            publish(request, "", result);
            return result;
        }
        if (rules.isEmpty()) {
            RateLimitResult result = RateLimitResult.noMatchingRule(request.requestId());
            publish(request, "", result);
            return result;
        }

        RateLimitResult lastAllowed = null;
        for (DistributedRateLimitRule rule : rules) {
            RateLimitResult result = tryAcquireRule(request, rule);
            publish(request, rule.ruleId(), result);
            if (!result.permitted()) {
                return result;
            }
            lastAllowed = result;
        }
        return lastAllowed == null ? RateLimitResult.noMatchingRule(request.requestId()) : lastAllowed;
    }

    /**
     * 关闭客户端资源。
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeQuietly(options.quotaGateway());
            closeQuietly(options.ruleProvider());
        }
    }

    private RateLimitResult tryAcquireRule(RateLimitRequest request, DistributedRateLimitRule rule) {
        if (!rule.hasDigest()) {
            return fallback(rule, RateLimitDecision.INVALID_REQUEST, "distributed_rule_digest_missing", "RULE_DIGEST_MISSING");
        }

        String quotaKey = rule.resolveQuotaKey(request);
        String shardKey = RendezvousHashOwnerSelector.shardKey(request.applicationCode(), rule.ruleId(), quotaKey);
        SelectedOwner owner = null;
        RateLimitDecision lastDecision = RateLimitDecision.CLIENT_ERROR;
        String lastReason = "quota_request_failed";

        for (int attempt = 1; attempt <= options.maxAcquireAttempts(); attempt++) {
            try {
                if (owner == null) {
                    owner = options.topologyManager().ownerOf(shardKey);
                }
                QuotaGatewayResponse response = options.quotaGateway().acquire(new AcquireQuotaCommand(
                        request.requestId(),
                        request.applicationCode(),
                        options.clientId(),
                        rule,
                        quotaKey,
                        request.cost() > 0 ? request.cost() : rule.cost(),
                        request.attributeView(),
                        owner));
                RateLimitDecision decision = response.decision();
                lastDecision = decision;
                lastReason = response.reason().isBlank() ? decision.name() : response.reason();
                switch (decision) {
                    case ALLOWED -> {
                        return RateLimitResult.allowed(
                                nonBlank(response.ruleId(), rule.ruleId()),
                                nonBlank(response.ruleRevision(), rule.revision()),
                                nonBlank(response.ruleChecksum(), rule.checksum()),
                                response.remaining(),
                                response.resetAtUnixMs(),
                                lastReason);
                    }
                    case DENIED -> {
                        return RateLimitResult.denied(
                                nonBlank(response.ruleId(), rule.ruleId()),
                                nonBlank(response.ruleRevision(), rule.revision()),
                                nonBlank(response.ruleChecksum(), rule.checksum()),
                                response.remaining(),
                                response.resetAtUnixMs(),
                                response.retryAfterMs(),
                                lastReason);
                    }
                    case NOT_OWNER -> {
                        emitRedirect(request, rule, response);
                        if (topologyRevisionChanged(owner, response)) {
                            options.topologyManager().refresh();
                            owner = null;
                        } else {
                            owner = redirectedOwner(owner, response.ownerInstance());
                            if (owner == null) {
                                options.topologyManager().refresh();
                                owner = null;
                            }
                        }
                    }
                    case SHARD_MIGRATING, SERVER_RULE_LAG -> sleep(response.retryAfterMs());
                    case RULE_STALE, RULE_CONFLICT -> {
                        DistributedRateLimitRule refreshedRule = refreshRule(request, rule);
                        if (!sameDigest(rule, refreshedRule)) {
                            rule = refreshedRule;
                            if (!rule.hasDigest()) {
                                return fallback(
                                        rule,
                                        RateLimitDecision.INVALID_REQUEST,
                                        "distributed_rule_digest_missing",
                                        "RULE_DIGEST_MISSING");
                            }
                            quotaKey = rule.resolveQuotaKey(request);
                            shardKey = RendezvousHashOwnerSelector.shardKey(
                                    request.applicationCode(),
                                    rule.ruleId(),
                                    quotaKey);
                            owner = null;
                        }
                        sleep(response.retryAfterMs());
                    }
                    case RULE_NOT_FOUND, INVALID_REQUEST -> {
                        return fallback(rule, decision, lastReason, decision.name());
                    }
                    default -> {
                        return fallback(rule, decision, lastReason, decision.name());
                    }
                }
            } catch (RuntimeException e) {
                log.warn(
                        "failed to acquire distributed quota, requestId={}, ruleId={}, attempt={}",
                        request.requestId(),
                        rule.ruleId(),
                        attempt,
                        e);
                lastDecision = RateLimitDecision.CLIENT_ERROR;
                lastReason = e.getClass().getSimpleName();
            }
        }
        return fallback(rule, lastDecision, lastReason, lastDecision.name());
    }

    private SelectedOwner redirectedOwner(SelectedOwner currentOwner, PulsarInstance redirectedInstance) {
        if (currentOwner == null || redirectedInstance == null) {
            return null;
        }
        return currentOwner.redirect(redirectedInstance);
    }

    private static boolean topologyRevisionChanged(SelectedOwner currentOwner, QuotaGatewayResponse response) {
        return currentOwner != null
                && !response.topologyRevision().isBlank()
                && !currentOwner.topology().topologyRevision().equals(response.topologyRevision());
    }

    private DistributedRateLimitRule refreshRule(RateLimitRequest request, DistributedRateLimitRule currentRule) {
        try {
            if (options.ruleProvider() instanceof RefreshableDistributedRateLimitRuleProvider refreshable) {
                refreshable.refresh();
            }
            return options.ruleProvider().find(request).stream()
                    .filter(rule -> rule.ruleId().equals(currentRule.ruleId()))
                    .findFirst()
                    .orElse(currentRule);
        } catch (RuntimeException e) {
            log.warn(
                    "failed to reload distributed rate limit rule, requestId={}, ruleId={}",
                    request.requestId(),
                    currentRule.ruleId(),
                    e);
            return currentRule;
        }
    }

    private static boolean sameDigest(DistributedRateLimitRule left, DistributedRateLimitRule right) {
        return left.ruleId().equals(right.ruleId())
                && left.revision().equals(right.revision())
                && left.checksum().equals(right.checksum());
    }

    private RateLimitResult fallback(
            DistributedRateLimitRule rule,
            RateLimitDecision sourceDecision,
            String reason,
            String errorCode) {
        FailPolicy failPolicy = rule.failPolicy() == null ? options.defaultFailPolicy() : rule.failPolicy();
        return RateLimitResult.fallback(
                failPolicy,
                sourceDecision,
                rule.ruleId(),
                rule.revision(),
                rule.checksum(),
                reason,
                errorCode);
    }

    private void sleep(long retryAfterMs) {
        long delayMs = retryAfterMs > 0 ? retryAfterMs : options.retryDelay().toMillis();
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StellpulsarClientException("interrupted while waiting for quota retry", e);
        }
    }

    private void publish(RateLimitRequest request, String ruleId, RateLimitResult result) {
        try {
            StellpulsarEventType type = eventType(result);
            options.eventListener().onEvent(StellpulsarEvent.fromResult(type, request.requestId(), ruleId, result));
        } catch (RuntimeException e) {
            log.warn("StellPulsar event listener failed, requestId={}, ruleId={}", request.requestId(), ruleId, e);
        }
    }

    private void emitRedirect(RateLimitRequest request, DistributedRateLimitRule rule, QuotaGatewayResponse response) {
        try {
            options.eventListener().onEvent(new StellpulsarEvent(
                    StellpulsarEventType.TOPOLOGY_REDIRECT,
                    request.requestId(),
                    rule.ruleId(),
                    null,
                    null,
                    response.ownerInstance() == null
                            ? java.util.Map.of("reason", "owner_missing")
                            : java.util.Map.of("ownerInstanceId", response.ownerInstance().instanceId())));
        } catch (RuntimeException e) {
            log.warn("StellPulsar redirect event listener failed, requestId={}, ruleId={}", request.requestId(), rule.ruleId(), e);
        }
    }

    private static StellpulsarEventType eventType(RateLimitResult result) {
        if (result.fallback()) {
            return result.permitted() ? StellpulsarEventType.FALLBACK_PERMITTED : StellpulsarEventType.FALLBACK_REJECTED;
        }
        return result.permitted() ? StellpulsarEventType.PERMITTED : StellpulsarEventType.REJECTED;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new StellpulsarClientException("StellPulsar client is closed");
        }
    }

    private static void closeQuietly(Object target) {
        if (target instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Best effort cleanup.
            }
        }
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
