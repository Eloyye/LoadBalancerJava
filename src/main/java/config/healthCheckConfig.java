package config;

import java.nio.file.Path;

public record HealthCheckConfig(
    int duration,
    int timeout,
    int maxTries,
    int successiveSuccessThreshold, // In a state where a pod is suspended, successive success threshold for redeclaring healthy
    long initialDelayMs,
    long maxDelayMs,
    Path healthCheckPath
) {}
