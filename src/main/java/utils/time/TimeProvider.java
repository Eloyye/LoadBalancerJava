package utils.time;

public interface TimeProvider {
    void sleep(long millis) throws InterruptedException;
}
