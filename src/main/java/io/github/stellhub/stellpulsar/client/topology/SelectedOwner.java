package io.github.stellhub.stellpulsar.client.topology;

public record SelectedOwner(
        PulsarInstance instance,
        TopologySnapshot topology,
        String shardKey,
        String shardHash) {

    public SelectedOwner {
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null");
        }
        if (topology == null) {
            throw new IllegalArgumentException("topology must not be null");
        }
        if (shardKey == null || shardKey.isBlank()) {
            throw new IllegalArgumentException("shardKey must not be blank");
        }
        shardHash = shardHash == null ? "" : shardHash;
    }

    /**
     * 使用服务端 redirect owner 创建新的 owner 视图。
     */
    public SelectedOwner redirect(PulsarInstance redirectedInstance) {
        return new SelectedOwner(redirectedInstance, topology, shardKey, shardHash);
    }
}
