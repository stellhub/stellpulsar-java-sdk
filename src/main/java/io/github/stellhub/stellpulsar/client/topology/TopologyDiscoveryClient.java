package io.github.stellhub.stellpulsar.client.topology;

public interface TopologyDiscoveryClient {

    /**
     * 查询当前 StellPulsar 服务端拓扑。
     */
    TopologySnapshot listInstances(TopologyDiscoveryRequest request);
}
