package io.github.stellhub.stellpulsar.client.model;

public record ApiResponse(int statusCode, String body) {

    /**
     * 判断 HTTP 响应是否成功。
     */
    public boolean successful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
