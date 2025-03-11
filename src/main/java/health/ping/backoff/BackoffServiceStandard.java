package health.ping.backoff;

import health.ping.backoff.backoffFuncInterfaces.BackoffMainLogic;
import health.ping.backoff.backoffFuncInterfaces.BackoffRetryCleanupHandler;
import health.ping.backoff.backoffFuncInterfaces.BackoffRetryHandler;
import health.ping.backoff.backoffFuncInterfaces.BackoffTerminationHandler;
import logging.LoggerFactory;
import org.slf4j.Logger;
import utils.SuccessStatus;
import utils.error.NetworkUnavailableException;

import java.util.concurrent.ExecutorService;

public class BackoffServiceStandard {
    private static final Logger logger = LoggerFactory.getLogger(BackoffServiceStandard.class);
    public static class BackoffBuilder {
        private static final Logger logger = LoggerFactory.getLogger(BackoffBuilder.class);
        
        private BackoffMainLogic mainLogic;
        private BackoffRetryHandler retryHandler = () -> {};
        private BackoffTerminationHandler terminationHandler = () -> {};
        private BackoffRetryCleanupHandler retryCleanupHandler = () -> {};
        private final ExecutorService executor;
        private int maxTries = 1;
        private long initialDelayMs = 100;
        private long maxDelayMs = 5000;
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
         * Executes the main logic function and only does retries if thrown NetworkUnavailableException
         * Uses exponential backoff strategy for retries
         *
         * @return SuccessStatus.SUCCESS if the main logic function was successful,
         * SuccessStatus.FAIL if the main logic function failed after maximum retries
         */
        public SuccessStatus execute() {
            logger.debug("Starting backoff execution with maxTries={}, initialDelay={}, maxDelay={}", 
                maxTries, initialDelayMs, maxDelayMs);
            
            var attempts = 0;
            var delay = initialDelayMs;
            
            while (attempts < maxTries) {
                try {
                    this.mainLogic.execute();
                    
                    // if we failed once, we need to cleanup
                    if (attempts > 0) {
                        logger.info("Execution succeeded after {} retries, running cleanup", attempts);
                        this.retryCleanupHandler.onRetryCleanup();
                    }
                    
                    return SuccessStatus.SUCCESS;
                } catch (NetworkUnavailableException e) {
                    if (attempts == 0) {
                        logger.info("Network unavailable, starting retry process");
                        this.retryHandler.onRetry();
                    }
                    
                    attempts++;
                    logger.debug("Retry attempt {} of {}, waiting for {}ms before next attempt", 
                        attempts, maxTries, delay);
                    
                    // Sleep with exponential backoff
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        logger.warn("Interrupted during backoff delay", ie);
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during backoff", ie);
                    }
                    
                    long newDelay = Math.min(delay * backoffFactor, maxDelayMs);
                    logger.debug("Increasing backoff delay from {}ms to {}ms", delay, newDelay);
                    delay = newDelay;
                }
            }
            
            logger.warn("Execution failed after {} attempts, running termination handler", maxTries);
            this.terminationHandler.onTerminate();
            return SuccessStatus.FAIL;
        }
    }

    public static BackoffBuilder run(BackoffMainLogic mainLogicFunction, ExecutorService executorService, long initialDelayMs, long maxDelayMs) {
        logger.debug("Creating new BackoffBuilder with initialDelayMs={}, maxDelayMs={}", initialDelayMs, maxDelayMs);
        return new BackoffBuilder(mainLogicFunction, executorService).initialDelayMs(initialDelayMs).maxDelayMs(maxDelayMs);
    }
}
