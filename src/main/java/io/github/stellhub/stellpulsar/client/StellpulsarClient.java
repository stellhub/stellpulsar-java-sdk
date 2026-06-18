package io.github.stellhub.stellpulsar.client;

import io.github.stellhub.stellpulsar.client.model.ApiResponse;
import io.github.stellhub.stellpulsar.client.model.LimitCheckRequest;

public interface StellpulsarClient extends AutoCloseable {

    /**
     * 请求限流决策。
     */
    ApiResponse check(LimitCheckRequest request);

    /**
     * 关闭客户端资源。
     */
    @Override
    void close();
}
