package io.github.stellhub.stellpulsar.client;

import io.github.stellhub.stellpulsar.client.model.RateLimitRequest;
import io.github.stellhub.stellpulsar.client.model.RateLimitResult;

public interface StellpulsarClient extends AutoCloseable {

    /**
     * 启动客户端。
     */
    void start();

    /**
     * 尝试获取分布式限流配额。
     */
    RateLimitResult tryAcquire(RateLimitRequest request);

    /**
     * 判断当前请求是否应被限流。
     */
    default boolean isLimited(RateLimitRequest request) {
        return !tryAcquire(request).permitted();
    }

    /**
     * 关闭客户端资源。
     */
    @Override
    void close();
}
