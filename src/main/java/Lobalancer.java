import com.sun.net.httpserver.HttpServer;
import health.HealthCheck;
import health.HealthCheckService;
import pods.BackendPod;
import server.RoundRobinLoadBalancer;
import server.handler.RootHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
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
            HealthCheckService<BackendPod> healthService = new HealthCheck<BackendPod>(executor);

            var httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.setExecutor(executor);
            var server = new RoundRobinLoadBalancer(port, healthService, executor, httpServer);
            var backendPod = new BackendPod(URI.create("http://127.0.0.1:8000"), false, 0, false);
            server.register(backendPod);
            server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
