package server.serverType;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;

import com.sun.net.httpserver.HttpServer;

import pods.BackendPod;
import repository.BackendPodInMemoryStore;
import server.LoadDistributable;
import server.handler.RootHandler;
import server.handler.LoadBalancerRegisterHandler;

public class LoadBalancerHttpService {
    private final HttpServer httpServer;
    private final LoadDistributable<BackendPod> loadBalancer;
    private final ExecutorService executor;
    private final HttpClient httpClient;
    private final BackendPodInMemoryStore inMemoryStore;

    public LoadBalancerHttpService(HttpServer httpServer, HttpClient httpClient, LoadDistributable<BackendPod> loadBalancer, ExecutorService executor, BackendPodInMemoryStore inMemoryStore) {
        this.httpServer = httpServer;
        this.loadBalancer = loadBalancer;
        this.executor = executor;
        this.httpClient = httpClient;
        this.inMemoryStore = inMemoryStore;
    }

    private void setupHandlers() {
        this.httpServer.createContext("/", new RootHandler(this.httpClient, this.loadBalancer));
        this.httpServer.createContext("/lbregister", new LoadBalancerRegisterHandler(this.inMemoryStore));
    }
    
    public void start() {
        this.setupHandlers();
        this.httpServer.start();
    }
}
