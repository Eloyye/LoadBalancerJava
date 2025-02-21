package health.ping.backoff.backoffFuncInterfaces;

@FunctionalInterface
public interface BackoffTerminationHandler {
    void onTerminate();
}
