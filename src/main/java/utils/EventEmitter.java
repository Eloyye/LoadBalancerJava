package utils;

public interface EventEmitter<S extends EventSubscriber<E, T>, E, T> {
    void subscribe(E event, S subscriber);
    void publish(E event, T content);
    void unsubscribe(E event, S subscriber);
}
