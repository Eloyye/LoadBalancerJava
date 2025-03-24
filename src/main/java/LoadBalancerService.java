import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import config.LoadBalancerConfig;
import health.HealthCheckService;
import health.HealthCheckServiceMain;
import health.ping.HealthCheckPingFactory;
import health.ping.Probeable;
import pods.BackendPod;
import repository.BackendPodInMemoryStore;
import server.RoundRobinLoadBalancer;
import server.serverType.LoadBalancerHttpService;
import utils.argparse.LobalancerArguments;
import utils.network.NetworkMethod;
import utils.time.RealTimeProvider;

public class LoadBalancerService {

    private final LoadBalancerConfig config;
    private final BackendPodInMemoryStore inMemoryStore;
    private final RealTimeProvider timeProvider;
    private final HealthCheckService<BackendPod> healthService;
    private final RoundRobinLoadBalancer loadBalancerAlgorithm;
    private final LoadBalancerHttpService loadBalancerServer;
    private final ExecutorService executor;
    private final Probeable<BackendPod> probeService;

    public LoadBalancerService(LobalancerArguments arguments) throws IOException {
        this.config = LoadBalancerConfig.fromConfigFile(arguments.getConfigFilePath());
        this.inMemoryStore = BackendPodInMemoryStore.getStore();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.timeProvider = new RealTimeProvider();
        this.probeService = HealthCheckPingFactory.create(NetworkMethod.HTTP, config, executor);
        this.healthService = new HealthCheckServiceMain(
            executor,
            config,
            inMemoryStore,
            timeProvider,
            probeService
            );
        this.loadBalancerAlgorithm = new RoundRobinLoadBalancer(this.inMemoryStore);
        var httpServer = HttpServer.create(new InetSocketAddress(config.port()), 0);
        httpServer.setExecutor(executor);
        var httpClient = HttpClient.newBuilder()
                .executor(executor)
                .build();
        this.loadBalancerServer = new LoadBalancerHttpService(httpServer, httpClient, loadBalancerAlgorithm, executor, inMemoryStore);
    }

    public void start() {
        this.healthService.start();
        this.loadBalancerServer.start();
    }
}