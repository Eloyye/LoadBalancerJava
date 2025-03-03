package health.ping.backoff.backoffFuncInterfaces;

@FunctionalInterface
public interface BackoffRetryCleanupHandler {
    void onRetryCleanup();
}
