package health.ping;

import utils.error.TimeoutRuntimeException;

import java.util.concurrent.TimeoutException;

public interface Pingable<T> {
    void ping(T networkInfo) throws TimeoutRuntimeException;
}
