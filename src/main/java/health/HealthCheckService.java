package health;

import health.types.HealthCheckResponse;

public interface HealthCheckService<T> {
    void start();

    HealthCheckResponse<String> sendHealthCheck(T pod);

}
