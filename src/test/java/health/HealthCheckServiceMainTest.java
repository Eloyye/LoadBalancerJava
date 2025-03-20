package health;

import config.LoadBalancerConfig;
import health.ping.Probeable;
import health.types.BackendPodStatus;
import health.types.HealthCheckResponse;
import health.types.HealthCheckServiceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pods.BackendPod;
import repository.BackendPodEvent;
import repository.BackendPodEventContext;
import repository.BackendPodInMemoryStore;
import utils.error.NetworkUnavailableException;
import utils.time.TimeProvider;

import java.net.URI;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HealthCheckServiceMainTest {

    @Mock
    private TimeProvider timeProvider;

    @Mock
    private Probeable<BackendPod> probeService;

    private ExecutorService executorService;
    private BackendPodInMemoryStore podStore;
    private LoadBalancerConfig healthCheckConfig;
    private HealthCheckServiceMain healthCheckService;

    private final URI healthyPodUri = URI.create("http://healthy-pod:8080");
    private final URI unhealthyPodUri = URI.create("http://unhealthy-pod:8080");
    private final URI intermittentPodUri = URI.create("http://intermittent-pod:8080");

    private BackendPod healthyPod;
    private BackendPod unhealthyPod;
    private BackendPod intermittentPod;

    @BeforeEach
    void setUp() {
        // Reset the pod store to ensure clean state for each test
        BackendPodInMemoryStore.resetInstance();
        podStore = BackendPodInMemoryStore.getStore();
        
        // Create a real executor service for testing
        executorService = Executors.newFixedThreadPool(5);
        
        // Configure health check with reasonable test values
        healthCheckConfig = new LoadBalancerConfig(
                100, // duration in ms
                Duration.ofMillis(500), // timeout
                3, // maxTries
                2, // successiveSuccessThreshold
                50, // initialDelayMs
                200, // maxDelayMs
                null, // networkMethod (not needed for tests)
                0, null  // healthCheckPath (not needed for tests)
        );
        
        // Initialize pods with different statuses
        healthyPod = new BackendPod(healthyPodUri, BackendPodStatus.ALIVE);
        unhealthyPod = new BackendPod(unhealthyPodUri, BackendPodStatus.ALIVE);
        intermittentPod = new BackendPod(intermittentPodUri, BackendPodStatus.ALIVE);
        
        // Initialize the health check service
        healthCheckService = new HealthCheckServiceMain(
                executorService,
                healthCheckConfig,
                podStore,
                timeProvider,
                probeService
        );
    }

    @Test
    void testSendHealthCheck_HealthyPod_ReturnsAliveStatus() throws Exception {
        // Arrange
        podStore.add(healthyPod);
        
        // Configure probe service to succeed for healthy pod
        doNothing().when(probeService).probe(healthyPod);
        
        // Act
        HealthCheckResponse<String> response = healthCheckService.sendHealthCheck(healthyPod);
        
        // Assert
        assertEquals(BackendPodStatus.ALIVE, response.backendPodStatus());
        assertEquals("success", response.content());
        verify(probeService, times(1)).probe(healthyPod);
        
        // Verify pod status in store remains ALIVE
        assertEquals(BackendPodStatus.ALIVE, podStore.get(healthyPodUri).status());
    }

    @Test
    void testSendHealthCheck_UnhealthyPod_ReturnsDeadStatus() throws Exception {
        // Arrange
        podStore.add(unhealthyPod);
        
        // Configure probe service to always fail for unhealthy pod
        doThrow(new NetworkUnavailableException("Connection refused")).when(probeService).probe(unhealthyPod);
        
        // Act
        HealthCheckResponse<String> response = healthCheckService.sendHealthCheck(unhealthyPod);
        
        // Assert
        assertEquals(BackendPodStatus.DEAD, response.backendPodStatus());
        assertEquals("dead", response.content());
        
        // Verify probe was called maxTries times
        verify(probeService, times(healthCheckConfig.maxTries())).probe(unhealthyPod);
        
        // Verify pod status in store is updated to DEAD
        assertEquals(BackendPodStatus.DEAD, podStore.get(unhealthyPodUri).status());
    }

    @Test
    void testSendHealthCheck_IntermittentPod_RecoversAfterFailures() throws Exception {
        // Arrange
        podStore.add(intermittentPod);
        
        // Configure probe service to fail twice then succeed
        doThrow(new NetworkUnavailableException("Connection timeout"))
            .doThrow(new NetworkUnavailableException("Connection timeout"))
            .doNothing()
            .when(probeService).probe(intermittentPod);
        
        // Act
        HealthCheckResponse<String> response = healthCheckService.sendHealthCheck(intermittentPod);
        
        // Assert
        assertEquals(BackendPodStatus.ALIVE, response.backendPodStatus());
        assertEquals("success", response.content());
        
        // Verify probe was called 3 times (2 failures + 1 success)
        verify(probeService, times(3)).probe(intermittentPod);
        
        // Verify pod status in store is updated to ALIVE after recovery
        assertEquals(BackendPodStatus.ALIVE, podStore.get(intermittentPodUri).status());
        
    }

    @Test
    void testSchedulePod_StartsPeriodicHealthChecks() throws Exception {
        // Arrange
        podStore.add(healthyPod);
        doNothing().when(probeService).probe(healthyPod);
        
        // Act
        healthCheckService.schedulePod(healthyPod);
        
        // Give some time for the scheduled task to execute
        Thread.sleep(300);
        
        // Assert - verify that probe was called at least once
        verify(probeService, atLeastOnce()).probe(healthyPod);
    }

    @Test
    void testHandleEvent_AddPodEvent_SchedulesHealthCheck() {
        // Arrange
        BackendPod newPod = new BackendPod(URI.create("http://new-pod:8080"), BackendPodStatus.ALIVE);
        BackendPodEventContext context = new BackendPodEventContext(
                BackendPodEvent.ADD_POD,
                ZonedDateTime.now(),
                List.of(newPod)
        );
        
        // Create a spy on the health check service to verify schedulePod is called
        HealthCheckServiceMain serviceSpy = spy(healthCheckService);
        
        // Act
        serviceSpy.handleEvent(BackendPodEvent.ADD_POD, context);
        
        // Assert
        verify(serviceSpy).schedulePod(newPod);
    }

    @Test
    void testStop_SuspendsHealthChecks() throws Exception {
        // Arrange
        podStore.add(healthyPod);
        doNothing().when(probeService).probe(healthyPod);
        healthCheckService.schedulePod(healthyPod);
        
        // Act
        healthCheckService.stop();
        
        // Assert
        assertTrue(healthCheckService.isStopped());
        assertEquals(HealthCheckServiceStatus.SUSPENDED, healthCheckService.getStatus());
        
        // Reset probe service mock to verify no more calls after stopping
        reset(probeService);
        
        // Wait to ensure no more health checks are performed
        Thread.sleep(200);
        verifyNoMoreInteractions(probeService);
    }

    @Test
    void testStart_InitiatesHealthChecksForAllAlivePods() {
        // Arrange
        podStore.add(healthyPod);
        podStore.add(unhealthyPod);
        
        // Create a spy to verify schedulePod is called for each alive pod
        HealthCheckServiceMain serviceSpy = spy(healthCheckService);
        
        // Act
        serviceSpy.start();
        
        // Assert
        verify(serviceSpy).schedulePod(healthyPod);
        verify(serviceSpy).schedulePod(unhealthyPod);
    }

    @Test
    void testMultiplePods_ConcurrentHealthChecks() throws Exception {
        // Arrange - add multiple pods
        BackendPod pod1 = new BackendPod(URI.create("http://pod1:8080"), BackendPodStatus.ALIVE);
        BackendPod pod2 = new BackendPod(URI.create("http://pod2:8080"), BackendPodStatus.ALIVE);
        BackendPod pod3 = new BackendPod(URI.create("http://pod3:8080"), BackendPodStatus.ALIVE);
        
        podStore.add(pod1);
        podStore.add(pod2);
        podStore.add(pod3);
        
        // Configure probe service behavior
        doNothing().when(probeService).probe(pod1);
        doThrow(new NetworkUnavailableException("Connection refused")).when(probeService).probe(pod2);
        doNothing().when(probeService).probe(pod3);
        
        // Act
        healthCheckService.start();
        
        // Give time for concurrent health checks to execute
        Thread.sleep(500);
        
        // Assert
        verify(probeService, atLeastOnce()).probe(pod1);
        verify(probeService, atLeastOnce()).probe(pod2);
        verify(probeService, atLeastOnce()).probe(pod3);
        
        // Verify final pod statuses
        assertEquals(BackendPodStatus.ALIVE, podStore.get(pod1.uri()).status());
        assertEquals(BackendPodStatus.DEAD, podStore.get(pod2.uri()).status());
        assertEquals(BackendPodStatus.ALIVE, podStore.get(pod3.uri()).status());
    }
    
    @Test
    void testPodStatusTransitions_UnresponsiveToDeadToAlive() throws Exception {
        // Arrange
        BackendPod pod = new BackendPod(URI.create("http://transition-pod:8080"), BackendPodStatus.ALIVE);
        podStore.add(pod);
        
        // First call - make pod unresponsive but recover before max tries
        doThrow(new NetworkUnavailableException("Timeout"))
            .doNothing()
            .when(probeService).probe(pod);
        
        // Act - first health check
        HealthCheckResponse<String> response1 = healthCheckService.sendHealthCheck(pod);
        
        // Assert - pod recovers and stays alive
        assertEquals(BackendPodStatus.ALIVE, response1.backendPodStatus());
        assertEquals(BackendPodStatus.ALIVE, podStore.get(pod.uri()).status());
        
        // Reset mock for next scenario
        reset(probeService);
        
        // Second scenario - pod becomes completely unresponsive
        doThrow(new NetworkUnavailableException("Connection refused")).when(probeService).probe(pod);
        
        // Act - second health check
        HealthCheckResponse<String> response2 = healthCheckService.sendHealthCheck(pod);
        
        // Assert - pod is now dead
        assertEquals(BackendPodStatus.DEAD, response2.backendPodStatus());
        assertEquals(BackendPodStatus.DEAD, podStore.get(pod.uri()).status());
        
        // Reset mock for final scenario
        reset(probeService);
        
        // Update pod status manually to simulate pod coming back online
        podStore.update(pod.updateStatus(BackendPodStatus.ALIVE));
        
        // Configure probe to succeed
        doNothing().when(probeService).probe(pod);
        
        // Act - third health check
        HealthCheckResponse<String> response3 = healthCheckService.sendHealthCheck(pod);
        
        // Assert - pod is alive again
        assertEquals(BackendPodStatus.ALIVE, response3.backendPodStatus());
        assertEquals(BackendPodStatus.ALIVE, podStore.get(pod.uri()).status());
    }
    
    @Test
    void testConcurrentHealthChecks_OnlyOneThreadPerPod() throws Exception {
        // Arrange
        BackendPod pod = new BackendPod(URI.create("http://concurrent-pod:8080"), BackendPodStatus.ALIVE);
        podStore.add(pod);
        
        // Configure probe to take some time to simulate long-running health check
        doAnswer(invocation -> {
            Thread.sleep(200);
            return null;
        }).when(probeService).probe(pod);
        
        // Act - schedule the pod twice in quick succession
        healthCheckService.schedulePod(pod);
        healthCheckService.schedulePod(pod); // This should interrupt the first thread
        
        // Wait for execution
        Thread.sleep(200);
        
        // Assert - probe should be called at most twice
        // Once for the first thread that gets interrupted, and once for the second thread
        verify(probeService, atMost(2)).probe(pod);
    }
    
    @Test
    void testCleanupOnShutdown() throws Exception {
        // This test verifies that resources are properly cleaned up
        
        // Arrange
        podStore.add(healthyPod);
        healthCheckService.start();
        
        // Act
        healthCheckService.stop();
        executorService.shutdown();
        boolean terminated = executorService.awaitTermination(1, TimeUnit.SECONDS);
        
        // Assert
        assertTrue(terminated, "Executor service should terminate cleanly");
    }
}
