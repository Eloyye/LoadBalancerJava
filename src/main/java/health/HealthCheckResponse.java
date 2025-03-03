package health;

import java.time.ZonedDateTime;

public record HealthCheckResponse<T>(T content, BackendPodStatus backendPodStatus, ZonedDateTime timestamp) {

}
