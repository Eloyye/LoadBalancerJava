package health.types;

/**
 * Represents the operational state of a backend pod in the system.
 * <p>
 * A backend pod follows this lifecycle:
 * <ul>
 *   <li>INITIALIZING: Initial state after adding a backend pod but before verifying it can receive requests</li>
 *   <li>ALIVE: Pod is healthy and expected to receive requests</li>
 *   <li>UNRESPONSIVE: Pod is temporarily not responding to requests</li>
 *   <li>DEAD: Pod has remained unresponsive for too long and will no longer be considered for forwarding requests</li>
 * </ul>
 */

public enum BackendPodStatus {
    ALIVE,
    INITIALIZING,
    UNRESPONSIVE,
    DEAD
}
