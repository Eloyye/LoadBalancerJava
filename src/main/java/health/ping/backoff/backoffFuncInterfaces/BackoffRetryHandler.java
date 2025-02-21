package health.ping.backoff.backoffFuncInterfaces;

@FunctionalInterface
public interface BackoffRetryHandler {
    void onRetry();
}
