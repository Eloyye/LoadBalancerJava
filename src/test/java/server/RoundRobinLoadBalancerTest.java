package server;

import com.sun.net.httpserver.HttpServer;
import health.HealthCheckService;
import health.types.BackendPodStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pods.BackendPod;
import repository.BackendPodInMemoryStore;
import server.handler.RootHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RoundRobinLoadBalancerTest {

    @Mock
    private HealthCheckService<BackendPod> healthCheckService;
    
    @Mock
    private HttpServer httpServer;
    
    @Mock
    private ExecutorService executorService;

    private RoundRobinLoadBalancer loadBalancer;

    private BackendPodInMemoryStore inMemoryStore;
    
    // Test URIs
    private final URI POD1_URI = URI.create("http://127.0.0.1:8000");
    private final URI POD2_URI = URI.create("http://127.0.0.1:8001");
    private final URI POD3_URI = URI.create("http://127.0.0.1:8002");

    @BeforeEach
    void setUp() throws IOException {
        // Set up the HttpServer mock to return a valid socket address
        when(httpServer.getAddress()).thenReturn(new InetSocketAddress("localhost", 8080));
        
        // Initialize the load balancer with mocked dependencies
        this.inMemoryStore = BackendPodInMemoryStore.getStore();
        this.loadBalancer = new RoundRobinLoadBalancer(this.inMemoryStore);
        
        // Verify that the server context was created during initialization
        verify(httpServer).createContext(eq("/"), any(RootHandler.class));
    }
    
    @AfterEach
    void tearDown() {
        // No explicit cleanup needed for mocks
    }

    @Test
    public void testRegister_SinglePod() {
        // Arrange
        BackendPod backendPod = new BackendPod(POD1_URI, BackendPodStatus.ALIVE);
        
        // Act
        loadBalancer.register(backendPod);
        
        // Assert
        assertEquals(backendPod, loadBalancer.next());
    }

    @Test
    public void testRegister_MultiplePods() {
        // Arrange
        BackendPod pod1 = new BackendPod(POD1_URI, BackendPodStatus.ALIVE);
        BackendPod pod2 = new BackendPod(POD2_URI, BackendPodStatus.ALIVE);
        
        // Act
        loadBalancer.register(pod1);
        loadBalancer.register(pod2);
        
        // Assert - should get pods in registration order
        assertEquals(pod1, loadBalancer.next());
        assertEquals(pod2, loadBalancer.next());
        // Should cycle back to the first pod
        assertEquals(pod1, loadBalancer.next());
    }

    @Test
    public void testNext_EmptyPodList_ThrowsException() {
        // Assert
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            loadBalancer.next();
        });
        
        assertEquals("incorrect number of backend pods", exception.getMessage());
    }

    @Test
    public void testNext_RoundRobinDistribution() {
        // Arrange
        BackendPod pod1 = new BackendPod(POD1_URI, BackendPodStatus.ALIVE);
        BackendPod pod2 = new BackendPod(POD2_URI, BackendPodStatus.ALIVE);
        BackendPod pod3 = new BackendPod(POD3_URI, BackendPodStatus.ALIVE);
        
        loadBalancer.register(pod1);
        loadBalancer.register(pod2);
        loadBalancer.register(pod3);
        
        // Act & Assert - verify round robin distribution
        assertEquals(pod1, loadBalancer.next());
        assertEquals(pod2, loadBalancer.next());
        assertEquals(pod3, loadBalancer.next());
        assertEquals(pod1, loadBalancer.next()); // Should cycle back to first pod
    }

    @Test
    public void testNext_SkipsDeadPods() {
        // Arrange
        BackendPod alivePod1 = new BackendPod(POD1_URI, BackendPodStatus.ALIVE);
        BackendPod deadPod = new BackendPod(POD2_URI, BackendPodStatus.DEAD);
        BackendPod alivePod2 = new BackendPod(POD3_URI, BackendPodStatus.ALIVE);
        
        loadBalancer.register(alivePod1);
        loadBalancer.register(deadPod);
        loadBalancer.register(alivePod2);
        
        // Act & Assert
        assertEquals(alivePod1, loadBalancer.next()); // First alive pod
        assertEquals(alivePod2, loadBalancer.next()); // Skip dead pod, go to next alive
        assertEquals(alivePod1, loadBalancer.next()); // Back to first alive pod
    }

    @Test
    public void testNext_AllPodsAreDead() {
        // Arrange
        BackendPod deadPod1 = new BackendPod(POD1_URI, BackendPodStatus.DEAD);
        BackendPod deadPod2 = new BackendPod(POD2_URI, BackendPodStatus.DEAD);
        
        loadBalancer.register(deadPod1);
        loadBalancer.register(deadPod2);
        
        // Act & Assert - will keep checking all pods and return the last one checked
        // This is the current behavior of the implementation, which might need reconsideration
        Optional<BackendPod> result = loadBalancer.next();
        assertTrue(result.isPresent());
        assertEquals(BackendPodStatus.DEAD, result.get().status());
    }

    @Test
    public void testNext_PodStatusChanges() {
        // Arrange
        BackendPod pod1 = new BackendPod(POD1_URI, BackendPodStatus.ALIVE);
        BackendPod pod2 = new BackendPod(POD2_URI, BackendPodStatus.ALIVE);
        
        loadBalancer.register(pod1);
        loadBalancer.register(pod2);
        
        // Act & Assert - initial state
        assertEquals(pod1, loadBalancer.next());
        assertEquals(pod2, loadBalancer.next());
        
        // Update pod1 to DEAD status
        BackendPod deadPod1 = pod1.updateStatus(BackendPodStatus.DEAD);
        
        // Replace pod1 with deadPod1 in the load balancer's list (simulating an update)
        // This is a bit of a hack since we don't have direct access to update the list
        // In a real scenario, this would happen through the event system
        List<BackendPod> updatedPods = new ArrayList<>();
        updatedPods.add(deadPod1);
        updatedPods.add(pod2);
        
        // Use reflection to update the backendPods list
        try {
            java.lang.reflect.Field backendPodsField = RoundRobinLoadBalancer.class.getDeclaredField("backendPods");
            backendPodsField.setAccessible(true);
            backendPodsField.set(loadBalancer, updatedPods);
            
            // Reset the next pointer
            java.lang.reflect.Field nextField = RoundRobinLoadBalancer.class.getDeclaredField("next");
            nextField.setAccessible(true);
            nextField.set(loadBalancer, 0);
        } catch (Exception e) {
            fail("Failed to update load balancer state: " + e.getMessage());
        }
        
        // Act & Assert - should skip the dead pod
        assertEquals(pod2, loadBalancer.next());
        assertEquals(pod2, loadBalancer.next()); // Still pod2 since pod1 is dead
    }

    @Test
    public void testConcurrentNext() throws Exception {
        // Arrange
        BackendPod pod1 = new BackendPod(POD1_URI, BackendPodStatus.ALIVE);
        BackendPod pod2 = new BackendPod(POD2_URI, BackendPodStatus.ALIVE);
        
        loadBalancer.register(pod1);
        loadBalancer.register(pod2);
        
        // Create a real load balancer for this test to avoid mocking synchronized behavior
        HttpServer realServer = HttpServer.create(new InetSocketAddress(0), 0);
        ExecutorService realExecutor = Executors.newFixedThreadPool(10);
        RoundRobinLoadBalancer realLoadBalancer = new RoundRobinLoadBalancer(this.inMemoryStore);
        
        realLoadBalancer.register(pod1);
        realLoadBalancer.register(pod2);
        
        // Act - simulate concurrent access from multiple threads
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        List<BackendPod> results = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                synchronized (results) {
                    results.add(realLoadBalancer.next().get());
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Assert
        assertEquals(threadCount, results.size());
        
        // Cleanup
        realExecutor.shutdown();
    }

    @Test
    public void testNextWithUnresponsivePods() {
        // Arrange
        BackendPod alivePod = new BackendPod(POD1_URI, BackendPodStatus.ALIVE);
        BackendPod unresponsivePod = new BackendPod(POD2_URI, BackendPodStatus.UNRESPONSIVE);
        
        loadBalancer.register(alivePod);
        loadBalancer.register(unresponsivePod);
        
        // Act & Assert
        // The current implementation only skips DEAD pods, not UNRESPONSIVE ones
        assertEquals(alivePod, loadBalancer.next());
        assertEquals(unresponsivePod, loadBalancer.next()); // Should not skip UNRESPONSIVE
        assertEquals(alivePod, loadBalancer.next());
    }
}