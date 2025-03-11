package config;

import utils.network.NetworkMethod;

import java.nio.file.Path;
import java.time.Duration;

public record HealthCheckConfig(
    int duration,
    Duration timeout,
    int maxTries,
    int successiveSuccessThreshold, // In a state where a pod is suspended, successive success threshold for redeclaring healthy
    long initialDelayMs,
    long maxDelayMs,
    NetworkMethod networkMethod,
    Path healthCheckPath
) {
    public static HealthCheckConfig fromConfigFile() {
        // TODO: Implement fromConfigFile
        return new HealthCheckConfig(0, Duration.ofMillis(0), 0, 0, 0, 0, NetworkMethod.HTTP, Path.of("/"));
    }
}
