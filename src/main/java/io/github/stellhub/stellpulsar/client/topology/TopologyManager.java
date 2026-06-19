package io.github.stellhub.stellpulsar.client.topology;

public interface TopologyManager {

    /**
     * 查询 shard key 的当前 owner。
     */
    SelectedOwner ownerOf(String shardKey);

    /**
     * 强制刷新拓扑。
     */
    TopologySnapshot refresh();
}
