package health.ping;

import utils.error.NetworkUnavailableException;

public interface Probeable<T> {
    void probe(T networkInfo) throws NetworkUnavailableException;
}
