package server;

import com.sun.net.httpserver.HttpServer;
import health.HealthCheckService;
import health.types.BackendPodStatus;
import pods.BackendPod;
import server.handler.RootHandler;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class RoundRobinLoadBalancer implements LoadDistributable<BackendPod> {
    private final ExecutorService executor;
    private final HttpServer server;
    private final List<BackendPod> backendPods;
    private final HealthCheckService<BackendPod> healthCheckService;
    private final int port;
    private int next;

    //    Refactor to support builder pattern
    public RoundRobinLoadBalancer(int port, HealthCheckService<BackendPod> healthCheckService, ExecutorService executor, HttpServer httpServer) throws IOException {
        this.port = port;
        this.executor = executor;
        this.server = httpServer;
        setupDefaultContext();
        this.backendPods = new ArrayList<>();
        this.healthCheckService = healthCheckService;
        this.next = 0;
    }


    private void setupDefaultContext() {
        /*
        * Setup path handlers for http server
        * */
        this.server.createContext("/",
                new RootHandler(HttpClient.newBuilder()
                        .executor(executor)
                        .build(), this));
    }

    @Override
    public void register(BackendPod backendPod) {
        this.backendPods.add(backendPod);
    }

    @Override
    synchronized public BackendPod next() {
        if (this.backendPods.isEmpty()) {
            throw new IllegalStateException("incorrect number of backend pods");
        }
        var nextBackendPod = this.backendPods.get(this.next);
        while (nextBackendPod.status() == BackendPodStatus.DEAD) {
//            TODO Refactor backendpods to handle "next" logic
            this.next = (this.next + 1) % this.backendPods.size();
            nextBackendPod = this.backendPods.get(this.next);
        }
        this.next = (this.next + 1) % this.backendPods.size();
        return nextBackendPod;
    }

    @Override
    public void start() {
        this.healthCheckService.start();
        this.server.start();
    }

    public int getPort() {
        return port;
    }

    // handle events from memory store
}
