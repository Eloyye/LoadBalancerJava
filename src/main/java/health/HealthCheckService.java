package health;

public interface HealthCheckService<T> {
    void start();

    HealthCheckResponse<String> sendHealthCheck(T pod);

}
