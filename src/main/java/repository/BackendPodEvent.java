package repository;

/**
 * Enumerates the types of events that can occur in the backend pod repository.
 * <p>
 * Each event type is associated with a specific action that can be taken in response to the event.
 * <ul>
 *   <li>ADD_POD: A new backend pod is added to the repository</li>
 *   <li>REMOVE_POD: A backend pod is removed from the repository</li>
 *   <li>UPDATE_POD: A backend pod is updated in the repository</li>
 *   <li>POD_READY: A backend pod is ready to receive traffic</li>
 * </ul>
 */
public enum BackendPodEvent {
    ADD_POD,
    REMOVE_POD,
    UPDATE_POD,
    POD_READY
}
