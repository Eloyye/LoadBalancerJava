package health.ping;

import pods.BackendPod;
import utils.error.TimeoutRuntimeException;

import java.util.concurrent.ExecutorService;

public class HealthCheckHTTP implements Pingable<BackendPod> {
    private int timeout;
    private ExecutorService executor;

    public HealthCheckHTTP() {

    }

    public HealthCheckHTTP(int duration, ExecutorService executorService) {
        this.timeout = duration;
        this.executor = executorService;
    }


    /**
     * @param networkInfo
     */
    @Override
    public void ping(BackendPod networkInfo) throws TimeoutRuntimeException {

    }

    public int getTimeout() {
        return timeout;
    }

    public HealthCheckHTTP setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public HealthCheckHTTP setExecutor(ExecutorService executor) {
        this.executor = executor;
        return this;
    }
}
