package repository;

import java.util.Collection;

/**
 * The interface Repository.
 *
 * @param <T> ID Parameter Type
 * @param <U> Result Type
 */
public interface Repository<T, U> {
    U get(T id);
    Collection<U> getAll();
    void add(U item);
    void remove(T id);
}
