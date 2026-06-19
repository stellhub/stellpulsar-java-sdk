package io.github.stellhub.stellpulsar.client.event;

public interface StellpulsarEventListener {

    StellpulsarEventListener NOOP = event -> {
    };

    /**
     * 处理 SDK 事件。
     */
    void onEvent(StellpulsarEvent event);
}
