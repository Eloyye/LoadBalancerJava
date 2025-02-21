package health;

import java.time.ZonedDateTime;

public record HealthCheckResponse<T>(T content, HealthCheckStatus healthCheckStatus, ZonedDateTime timestamp) {
}
