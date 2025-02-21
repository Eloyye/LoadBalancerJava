package health.ping.backoff.backoffFuncInterfaces;

import utils.error.TimeoutRuntimeException;

public interface BackoffMainLogic {
    void execute() throws TimeoutRuntimeException;
}
