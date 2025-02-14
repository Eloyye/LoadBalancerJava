package health;

import pods.BackendPod;
import server.LoadDistributable;
import server.RoundRobinLoadBalancer;

public interface HealthCheckService<T> {
    void start();

    void register(BackendPod backendPod);

    void subscribe(LoadDistributable<T> roundRobinLoadBalancer);
}
