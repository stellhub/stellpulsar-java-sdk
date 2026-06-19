package io.github.stellhub.stellpulsar.client.topology;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultTopologyManager implements TopologyManager {

    private final TopologyDiscoveryClient discoveryClient;
    private final TopologyDiscoveryRequest discoveryRequest;
    private final OwnerSelector ownerSelector;
    private final Clock clock;
    private final AtomicReference<TopologySnapshot> current = new AtomicReference<>();

    public DefaultTopologyManager(
            TopologyDiscoveryClient discoveryClient,
            TopologyDiscoveryRequest discoveryRequest,
            OwnerSelector ownerSelector) {
        this(discoveryClient, discoveryRequest, ownerSelector, Clock.systemUTC());
    }

    DefaultTopologyManager(
            TopologyDiscoveryClient discoveryClient,
            TopologyDiscoveryRequest discoveryRequest,
            OwnerSelector ownerSelector,
            Clock clock) {
        this.discoveryClient = Objects.requireNonNull(discoveryClient, "discoveryClient must not be null");
        this.discoveryRequest = Objects.requireNonNull(discoveryRequest, "discoveryRequest must not be null");
        this.ownerSelector = Objects.requireNonNull(ownerSelector, "ownerSelector must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 查询 shard key 的当前 owner。
     */
    @Override
    public SelectedOwner ownerOf(String shardKey) {
        TopologySnapshot snapshot = current.get();
        if (snapshot == null || snapshot.expired(clock)) {
            snapshot = refresh();
        }
        return ownerSelector.select(snapshot, shardKey);
    }

    /**
     * 强制刷新拓扑。
     */
    @Override
    public TopologySnapshot refresh() {
        TopologySnapshot snapshot = discoveryClient.listInstances(discoveryRequest);
        current.set(snapshot);
        return snapshot;
    }
}
