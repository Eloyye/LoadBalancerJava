package health.ping;

import config.LoadBalancerConfig;
import pods.BackendPod;
import utils.network.NetworkMethod;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;

public class HealthCheckPingFactory {
    public static Probeable<BackendPod> create(NetworkMethod networkType, LoadBalancerConfig healthCheckConfig, ExecutorService executorService) {
        switch (networkType) {
            case NetworkMethod.HTTP -> {
                HttpClient httpClient = HttpClient.newBuilder()
                        .executor(executorService)
                        .version(HttpClient.Version.HTTP_2)
                        .connectTimeout(healthCheckConfig.timeout())
                        .build();
                return new HealthProbeHttp(healthCheckConfig, httpClient);
            }
            default -> {
                throw new UnsupportedOperationException("Unable to build; Invalid network type: %s".formatted(networkType));
            }
        }
    }
}
