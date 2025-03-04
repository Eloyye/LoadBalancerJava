package health.ping.backoff;

import health.ping.backoff.backoffFuncInterfaces.BackoffMainLogic;
import health.ping.backoff.backoffFuncInterfaces.BackoffRetryCleanupHandler;
import health.ping.backoff.backoffFuncInterfaces.BackoffRetryHandler;
import health.ping.backoff.backoffFuncInterfaces.BackoffTerminationHandler;
import utils.SuccessStatus;
import utils.error.NetworkUnavailableException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class BackoffServiceStandard {
    public static class BackoffBuilder {
        private BackoffMainLogic mainLogic;
        private BackoffRetryHandler retryHandler = () -> {};
        private BackoffTerminationHandler terminationHandler = () -> {};
        private BackoffRetryCleanupHandler retryCleanupHandler = () -> {};
        private final ExecutorService executor;
        private int maxTries = 1;
        private long initialDelayMs = 100;
        private long  maxDelayMs = 5000;
        private int backoffFactor = 2;

        public BackoffBuilder(BackoffMainLogic mainLogicFunction, ExecutorService executor) {
            this.mainLogic = mainLogicFunction;
            this.executor = executor;
        }

        public BackoffBuilder initialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
            return this;
        }

        public BackoffBuilder maxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
            return this;
        }

        public BackoffBuilder onRetry(BackoffRetryHandler retryHandler, int maxTries) {
            this.retryHandler = retryHandler;
            this.maxTries = maxTries;
            return this;
        }

        public BackoffBuilder onRetry(BackoffRetryHandler retryHandler) {
            this.retryHandler = retryHandler;
            return this;
        }

        public BackoffBuilder onTermination(BackoffTerminationHandler terminationHandler) {
            this.terminationHandler = terminationHandler;
            return this;
        }

        public BackoffBuilder onRetryCleanup(BackoffRetryCleanupHandler retryCleanupHandler) {
            this.retryCleanupHandler  = retryCleanupHandler;
            return this;
        }

        public BackoffBuilder backoffFactor(int backoffFactor) {
            this.backoffFactor = backoffFactor;
            return this;
        }

        /**
         *
         * Executes the main logic function and only does retries if thrown NetworkUnavailableException
         *
         * @return SuccessStatus.SUCCESS if the main logic function was successful,
         * SuccessStatus.FAIL if the main logic function failed
         */
        public SuccessStatus execute() {
//            TODO Execute with back off
            // main logic with exponential backoff
            // retry
            var attempts = 0;
            var delay = initialDelayMs;
            while (attempts < maxTries) {
                try {
                    this.mainLogic.execute();
                    // if we failed once, we need to cleanup
                    if (attempts > 0) {
                        this.retryCleanupHandler.onRetryCleanup();
                    }
                    return SuccessStatus.SUCCESS;
                } catch (NetworkUnavailableException e) {
                    if (attempts == 0) {
                        this.retryHandler.onRetry();
                    }
                    attempts++;
                    // Sleep with exponential backoff
                    try {
                        executor.wait(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during backoff", ie);
                    }
                    delay = Math.min(delay * backoffFactor, maxDelayMs);
                }
            }
            this.terminationHandler.onTerminate();
            return SuccessStatus.FAIL;
        }
    }

    public static BackoffBuilder run(BackoffMainLogic mainLogicFunction, ExecutorService executorService, long initialDelayMs, long maxDelayMs) {
        return new BackoffBuilder(mainLogicFunction, executorService).initialDelayMs(initialDelayMs).maxDelayMs(maxDelayMs);
    }
}
