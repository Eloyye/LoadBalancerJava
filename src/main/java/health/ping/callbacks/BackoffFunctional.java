package health.ping.callbacks;

@FunctionalInterface
interface MainTask {
    void execute() throws Exception;
}

@FunctionalInterface
interface RetryHandler {
    void onRetry();
}

@FunctionalInterface
interface TerminationHandler {
    void onTerminate();
}
