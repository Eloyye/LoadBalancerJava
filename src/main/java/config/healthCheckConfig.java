package config;

public record healthCheckConfig(
    int duration,
    int timeout,
    int maxTries
) {}
