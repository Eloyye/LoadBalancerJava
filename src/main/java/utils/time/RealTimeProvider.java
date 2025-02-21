package utils.time;

class RealTimeProvider implements TimeProvider {
    @Override
    public void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}