package io.github.stellhub.stellpulsar.client;

public class StellpulsarClientException extends RuntimeException {

    public StellpulsarClientException(String message) {
        super(message);
    }

    public StellpulsarClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
