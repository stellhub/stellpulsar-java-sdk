package io.github.stellhub.stellpulsar.client.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RendezvousHashOwnerSelectorTest {

    @Test
    void selectIsStableWhenInstanceOrderChanges() {
        PulsarInstance a = instance("pulsar-a", 100);
        PulsarInstance b = instance("pulsar-b", 100);
        TopologySnapshot first = topology(List.of(a, b));
        TopologySnapshot second = topology(List.of(b, a));
        RendezvousHashOwnerSelector selector = new RendezvousHashOwnerSelector();

        SelectedOwner firstOwner = selector.select(first, "app:rule:key");
        SelectedOwner secondOwner = selector.select(second, "app:rule:key");

        assertEquals(firstOwner.instance().instanceId(), secondOwner.instance().instanceId());
        assertEquals(firstOwner.shardHash(), secondOwner.shardHash());
    }

    @Test
    void selectConsidersAllUpInstancesRegardlessOfPriority() {
        PulsarInstance high = instance("pulsar-high", 10);
        PulsarInstance low = instance("pulsar-low", 100);
        TopologySnapshot topology = topology(List.of(low, high));
        RendezvousHashOwnerSelector selector = new RendezvousHashOwnerSelector();

        for (int i = 0; i < 10_000; i++) {
            SelectedOwner owner = selector.select(topology, "app:rule:key-" + i);
            if ("pulsar-low".equals(owner.instance().instanceId())) {
                return;
            }
        }

        fail("expected a lower priority UP instance to be eligible as owner");
    }

    @Test
    void selectSkipsDrainingInstancesForNewOwner() {
        PulsarInstance draining = instance("pulsar-draining", 10, "DRAINING");
        PulsarInstance up = instance("pulsar-up", 100, "UP");
        TopologySnapshot topology = topology(List.of(draining, up));

        SelectedOwner owner = new RendezvousHashOwnerSelector().select(topology, "app:rule:key");

        assertEquals("pulsar-up", owner.instance().instanceId());
    }

    @Test
    void shardHashMatchesServiceDiagnosticFormat() {
        String hash = RendezvousHashOwnerSelector.shardHash(" rev-1 ", " app:rule:key ");

        assertEquals(16, hash.length());
        assertEquals(RendezvousHashOwnerSelector.sha256Hex("rev-1\napp:rule:key").substring(0, 16), hash);
    }

    private static TopologySnapshot topology(List<PulsarInstance> instances) {
        return new TopologySnapshot(
                "stellpulsar.v1",
                "rev-1",
                Long.MAX_VALUE,
                TopologySnapshot.RENDEZVOUS_HASH_V1,
                instances);
    }

    private static PulsarInstance instance(String id, int priority) {
        return instance(id, priority, "UP");
    }

    private static PulsarInstance instance(String id, int priority, String state) {
        return new PulsarInstance(
                id,
                "127.0.0.1",
                9090,
                priority,
                100,
                "",
                "",
                "",
                state,
                Map.of());
    }
}
