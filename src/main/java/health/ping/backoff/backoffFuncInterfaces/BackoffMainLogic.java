package health.ping.backoff.backoffFuncInterfaces;

import utils.error.NetworkUnavailableException;

public interface BackoffMainLogic {
    void execute() throws NetworkUnavailableException;
}
