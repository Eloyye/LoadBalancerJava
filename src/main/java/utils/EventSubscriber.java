package utils;


/**
 * The interface Event subscriber.
 *
 * @param <T> Event Type Parameter
 * @param <U> Content or Context Type Parameter
 * @param <V> Id Type parameter
 */
public interface EventSubscriber<T, U> {
    void handleEvent(T event, U content);
}
