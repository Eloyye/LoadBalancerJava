import com.sun.net.httpserver.HttpServer;
import config.HealthCheckConfig;
import health.HealthCheckServiceMain;
import health.HealthCheckService;
import health.ping.HealthCheckPingFactory;
import pods.BackendPod;
import repository.BackendPodInMemoryStore;
import server.RoundRobinLoadBalancer;
import utils.network.NetworkMethod;
import utils.time.RealTimeProvider;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;

public class Lobalancer {
    public static int getPort(String[] args) {
        int port;
        if (args.length < 1) {
            port = 80;
        } else {
            port = Integer.parseInt(args[0]);
        }
        return port;
    }

    public static void main(String[] args) {
        int port = getPort(args);
        try {
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            var config = HealthCheckConfig.fromConfigFile();
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
            var httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.setExecutor(executor);
            var server = new RoundRobinLoadBalancer(port, healthService, executor, httpServer);
            healthService.start();
            server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
