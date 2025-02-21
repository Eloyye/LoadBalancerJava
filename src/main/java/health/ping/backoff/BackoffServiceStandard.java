package health.ping.backoff;

import health.ping.backoff.backoffFuncInterfaces.BackoffMainLogic;
import health.ping.backoff.backoffFuncInterfaces.BackoffRetryHandler;
import health.ping.backoff.backoffFuncInterfaces.BackoffTerminationHandler;
import utils.SuccessStatus;

public class BackoffServiceStandard {
    public static class BackoffBuilder {
        private BackoffMainLogic mainLogic;
        private BackoffRetryHandler retryHandler = () -> {};
        private BackoffTerminationHandler terminationHandler = () -> {};
        private int maxTries = 1;

        public BackoffBuilder(BackoffMainLogic mainLogicFunction) {
            this.mainLogic = mainLogicFunction;
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

        public SuccessStatus execute() {
//            TODO Execute with back off
        }
    }

    public static BackoffBuilder run(BackoffMainLogic mainLogicFunction) {
        return new BackoffBuilder(mainLogicFunction);
    }
}
