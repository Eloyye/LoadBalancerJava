import com.sun.net.httpserver.HttpServer;
import config.LoadBalancerConfig;
import health.HealthCheckServiceMain;
import health.HealthCheckService;
import health.ping.HealthCheckPingFactory;
import picocli.CommandLine;
import pods.BackendPod;
import repository.BackendPodInMemoryStore;
import server.RoundRobinLoadBalancer;
import server.serverType.LoadBalancerHttpService;
import utils.argparse.LobalancerArguments;
import utils.network.NetworkMethod;
import utils.time.RealTimeProvider;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.Executors;

public class Lobalancer {
    private static final org.slf4j.Logger logger = logging.LoggerFactory.getLogger(Lobalancer.class);

    public static void main(String[] args) {
        
        logger.info("Initializing Lobalancer application");
        var arguments = new LobalancerArguments();
        new CommandLine(arguments).parseArgs(args);
        // ensure that config file is provided
        if (arguments.getConfigFilePath() == null) {
            logger.warn("No config file provided: using default settings");
        }
        try {
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            var config = LoadBalancerConfig.fromConfigFile(arguments.getConfigFilePath());
            logger.info("Using config: {}", config);
            var inMemoryStore = BackendPodInMemoryStore.getStore();
            var timeProvider = new RealTimeProvider();
            var probeService = HealthCheckPingFactory.create(NetworkMethod.HTTP, config, executor);
            HealthCheckService<BackendPod> healthService = new HealthCheckServiceMain(
                    executor,
                    config,
                    inMemoryStore,
                    timeProvider,
                    probeService
                    );
            var loadBalancerAlgorithm = new RoundRobinLoadBalancer(executor);
            var httpServer = HttpServer.create(new InetSocketAddress(config.port()), 0);
            httpServer.setExecutor(executor);
            var httpClient = HttpClient.newBuilder()
                    .executor(executor)
                    .build();
            var loadBalancerServer = new LoadBalancerHttpService(httpServer, httpClient, loadBalancerAlgorithm, executor, inMemoryStore);
            loadBalancerServer.start();
            healthService.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
