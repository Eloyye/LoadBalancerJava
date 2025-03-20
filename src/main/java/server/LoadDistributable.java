package server;

import java.util.Optional;

public interface LoadDistributable<T> {
    Optional<T> next();
    void register(T subscriber);
}
