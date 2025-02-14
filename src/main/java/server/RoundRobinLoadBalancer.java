package server;

import com.sun.net.httpserver.HttpServer;
import health.HealthCheckService;
import logging.LogLevel;
import logging.RuntimeLog;
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
        this.healthCheckService.subscribe(this);
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
        try {
            this.healthCheckService.register(backendPod);
        } catch (RuntimeException e) {
            // TODO
            throw new RuntimeException(e);
        }
    }

    @Override
    synchronized public BackendPod next() {
        if (this.backendPods.isEmpty()) {
            throw new IllegalStateException("incorrect number of backend pods");
        }
        var nextBackendPod = this.backendPods.get(this.next);
        while (nextBackendPod.isDead() || nextBackendPod.isMarkedForRemoval()) {
//            TODO Refactor backendpods to handle "next" logic
            this.next = (this.next + 1) % this.backendPods.size();
            nextBackendPod = this.backendPods.get(this.next);
        }
        this.next = (this.next + 1) % this.backendPods.size();
        return nextBackendPod;
    }

    @Override
    @RuntimeLog(value = "Starting Load Balancing Server...", level = LogLevel.INFO)
    public void start() {
        this.healthCheckService.start();
        this.server.start();
    }

    public int getPort() {
        return port;
    }
}
