package health.ping;

import utils.network.NETWORK;
import utils.patterns.Builder;

import java.util.concurrent.ExecutorService;

public class HealthCheckPingFactory {
    public static class HealthCheckHTTPBuilder implements Builder<HealthCheckHTTP> {

        private final HealthCheckHTTP healthCheckHTTP;

        public HealthCheckHTTPBuilder() {
            this.healthCheckHTTP = new HealthCheckHTTP();
        }

        public HealthCheckHTTPBuilder timeout(int duration) {
            this.healthCheckHTTP.setTimeout(duration);
            return this;
        }

        public HealthCheckHTTPBuilder executor(ExecutorService executorService) {
            this.healthCheckHTTP.setExecutor(executorService);
            return this;
        }

        /**
         * @return
         */
        @Override
        public HealthCheckHTTP build() {
            if (this.healthCheckHTTP.getExecutor() == null) {
                throw new IllegalStateException("Error trying to build HealthCheckHTTPBuilder: executorService is null");
            }
            return this.healthCheckHTTP;
        }
    }
    public static HealthCheckHTTPBuilder create(NETWORK networkType) {
        switch (networkType) {
            case NETWORK.HTTP -> {
                return new HealthCheckHTTPBuilder();
            }
            default -> {
                throw new UnsupportedOperationException("Unable to build; Invalid network type: %s".formatted(networkType));
            }
        }
    }
}
