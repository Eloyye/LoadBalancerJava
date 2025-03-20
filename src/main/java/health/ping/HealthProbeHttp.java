package health.ping;

import config.LoadBalancerConfig;
import pods.BackendPod;
import utils.error.NetworkUnavailableException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

public class HealthProbeHttp implements Probeable<BackendPod> {
    private Duration timeout;
    private final Path healthCheckPath;
    private final HttpClient httpClient;

    public HealthProbeHttp(LoadBalancerConfig healthCheckConfig, HttpClient httpClient) {
        this.timeout = healthCheckConfig.timeout();
        this.healthCheckPath = healthCheckConfig.healthCheckPath();
        this.httpClient = httpClient;
    }

    /**
     * @param networkInfo
     */
    @Override
    public void probe(BackendPod networkInfo) throws NetworkUnavailableException {
        HttpRequest requestBuilder = HttpRequest.newBuilder()
                .uri(networkInfo.uri().resolve(this.healthCheckPath.toString()))
                .timeout(this.timeout)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(requestBuilder, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode != 200) {
                throw new NetworkUnavailableException(response.body());
            }
        } catch (IOException e) {
            throw new NetworkUnavailableException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * Getters and Setters
     * */

    public Duration getTimeout() {
        return timeout;
    }

    public HealthProbeHttp setTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

}
