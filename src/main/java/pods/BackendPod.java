package pods;

import health.types.BackendPodStatus;

import java.net.URI;

public record BackendPod(
        URI uri,
        BackendPodStatus status
) {


    /**
     * Updates BackendPod
     * @return BackendPod: The new updated BackendPod
     */
    public BackendPod updateStatus(BackendPodStatus newStatus) {
        return new BackendPod(uri, newStatus);
    }
}
