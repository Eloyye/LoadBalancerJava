package health;

import pods.BackendPod;
import server.LoadDistributable;

import java.util.concurrent.ExecutorService;

public class HealthCheck<T> implements HealthCheckService<T> {
    private final ExecutorService executorService;

    public HealthCheck(ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     *
     */
    @Override
    public void start() {
//        TODO
        return;
    }

    /**
     * @param backendPod
     */
    @Override
    public void register(BackendPod backendPod) {
//        TODO
        return;
    }

    /**
     * @param roundRobinLoadBalancer
     */
    @Override
    public void subscribe(LoadDistributable<T> roundRobinLoadBalancer) {

    }
}
