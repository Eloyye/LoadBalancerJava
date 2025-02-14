package server;

public interface LoadDistributable<T> {
    T next();
    void register(T subscriber);
    void start();
}
