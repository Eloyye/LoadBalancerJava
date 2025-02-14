package server;

import com.sun.net.httpserver.HttpServer;
import health.HealthCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pods.BackendPod;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RoundRobinLoadBalancerTest {

    private RoundRobinLoadBalancer loadBalancer;
    private HealthCheckService<BackendPod> healthCheckService;
    private HttpServer httpServer;

    @BeforeEach
    void init() throws IOException {
        healthCheckService = mock(HealthCheckService.class);
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        loadBalancer = new RoundRobinLoadBalancer(8080, healthCheckService, Executors.newSingleThreadExecutor(), httpServer);
    }

    @Test
    public void register() {
        BackendPod backendPod = new BackendPod(URI.create("http://127.0.0.1:8000"), false, 0, false);
        loadBalancer.register(backendPod);
        assertEquals("http://127.0.0.1:8000", loadBalancer.next().uri().toString());
        verify(healthCheckService, times(1)).register(backendPod);
    }

    @Test
    public void next() {
        BackendPod backendPod1 = new BackendPod(URI.create("http://127.0.0.1:8000"), false, 0, false);
        BackendPod backendPod2 = new BackendPod(URI.create("http://127.0.0.1:8001"), false, 0, false);
        loadBalancer.register(backendPod1);
        loadBalancer.register(backendPod2);

        assertEquals(backendPod1, loadBalancer.next());
        assertEquals(backendPod2, loadBalancer.next());
        assertEquals(backendPod1, loadBalancer.next());
    }

    @Test
    public void nextBackendFail() {
        BackendPod backendPod1 = new BackendPod(URI.create("http://127.0.0.1:8000"), false, 0, false);
        BackendPod backendPod2 = new BackendPod(URI.create("http://127.0.0.1:8001"), false, 0, false);
        loadBalancer.register(backendPod1);
        loadBalancer.register(backendPod2);

        assertEquals(backendPod1, loadBalancer.next());
        assertEquals(backendPod2, loadBalancer.next());
        assertEquals(backendPod1, loadBalancer.next());



    }

    @Test
    public void start() {
        loadBalancer.start();
        verify(healthCheckService, times(1)).start();
        assertTrue(httpServer.getAddress().getPort() > 0);
    }
}