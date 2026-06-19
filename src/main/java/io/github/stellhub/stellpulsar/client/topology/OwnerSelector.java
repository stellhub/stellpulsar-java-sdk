package io.github.stellhub.stellpulsar.client.topology;

public interface OwnerSelector {

    /**
     * 根据 topology 和 shard key 选择 owner。
     */
    SelectedOwner select(TopologySnapshot topology, String shardKey);
}
