package pods;

import java.net.URI;

public record BackendPod(
        URI uri,
        boolean isDead,
        int reviveAttempts,
        boolean isMarkedForRemoval
//        add mutex?
) {
}
