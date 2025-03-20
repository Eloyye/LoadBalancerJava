package health;

import config.LoadBalancerConfig;
import health.ping.backoff.BackoffServiceStandard;
import health.ping.Probeable;
import health.types.BackendPodStatus;
import health.types.HealthCheckResponse;
import health.types.HealthCheckServiceStatus;
import logging.LoggerFactory;
import org.slf4j.Logger;
import pods.BackendPod;
import repository.BackendPodEvent;
import repository.BackendPodEventContext;
import repository.BackendPodInMemoryStore;
import utils.EventSubscriber;
import utils.SuccessStatus;
import utils.time.TimeProvider;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class HealthCheckServiceMain implements HealthCheckService<BackendPod>, EventSubscriber<BackendPodEvent, BackendPodEventContext> {
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckServiceMain.class);
    
    private final ExecutorService executorService;
    private final LoadBalancerConfig healthCheckConfig;
    private final BackendPodInMemoryStore podStore;
    private volatile HealthCheckServiceStatus status = HealthCheckServiceStatus.RUNNING;
    private final TimeProvider timeProvider;
    private final Probeable<BackendPod> probeService;
    private final Map<BackendPod, Thread> podThreads = new ConcurrentHashMap<>();

    public HealthCheckServiceMain(ExecutorService executorService,
                                  LoadBalancerConfig healthCheckConfig,
                                  BackendPodInMemoryStore podStore,
                                  TimeProvider timeProvider,
                                  Probeable<BackendPod> probeService
    ) {
        this.executorService = executorService;
        this.healthCheckConfig = healthCheckConfig;
        this.podStore = podStore;
        this.timeProvider = timeProvider;
        this.probeService = probeService;
    }

    /**
     * Start the health check service and send health checks to all registered pods
     */
    @Override
    public void start() {
        logger.info("Starting HealthCheckService");
        sendAllHealthChecks();
        logger.info("HealthCheckService started successfully");
        return;
    }

    private void sendAllHealthChecks() {
        // Send all health checks from podStore
        logger.debug("Sending health checks to all registered pods");
        int podCount = 0;
        for (BackendPod pod : this.podStore.getAll()) {
            if (pod.status() == BackendPodStatus.ALIVE) {
                schedulePod(pod);
                podCount++;
            }
        }
        logger.debug("Scheduled health checks for {} alive pods", podCount);
    }

    public void schedulePod(BackendPod pod) {
        logger.debug("Scheduling health check for pod: {}", pod.uri());
        startPeriodicTask(() -> sendHealthCheck(pod), pod);
    }

    public void startPeriodicTask(Supplier<HealthCheckResponse<String>> task, BackendPod pod) {
        this.executorService.submit(() -> {
            // constraint: there can only be one thread operating on a single pod
            if (podThreads.containsKey(pod)) {
                Thread inUseThread = podThreads.get(pod);
                // signal to terminate the thread
                logger.debug("Interrupting existing health check thread for pod: {}", pod.uri());
                inUseThread.interrupt();
                try {
                    inUseThread.join();
                    logger.debug("Successfully joined interrupted thread for pod: {}", pod.uri());
                } catch (InterruptedException e) {
                    // the thread running handles the interrupted exception
                    logger.error("Failed to join interrupted thread for pod: {}", pod.uri(), e);
                    throw new RuntimeException(e);
                }
            }
            this.podThreads.put(pod, Thread.currentThread());
            while (!this.isStopped() && !Thread.currentThread().isInterrupted()) {
                try {
                    var response = task.get();
                    // unresponsive means terminating health checking for current thread
                    if (response.backendPodStatus() == BackendPodStatus.DEAD) {
                        logger.info("Pod {} marked as DEAD, stopping health checks", pod.uri());
                        return;
                    }
                    logger.debug("Health check successful for pod: {}, sleeping for {}ms", 
                               pod.uri(), this.healthCheckConfig.duration());
                    this.timeProvider.sleep(this.healthCheckConfig.duration());
                } catch (InterruptedException e) {
                    logger.debug("Health check thread interrupted for pod: {}", pod.uri());
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            logger.debug("Ending health check thread for pod: {}", pod.uri());
            this.podThreads.remove(pod);
        });
    }

    public boolean isStopped() {
        return this.status == HealthCheckServiceStatus.SUSPENDED;
    }

    public void stop() {
        logger.info("Stopping HealthCheckService");
        this.status = HealthCheckServiceStatus.SUSPENDED;
        logger.info("HealthCheckService stopped");
    }

    public HealthCheckServiceStatus getStatus() {
        return this.status;
    }

    private void updatePodToStore(BackendPod pod, BackendPodStatus status) {
        logger.debug("Updating pod {} status to {}", pod.uri(), status);
        this.podStore.update(pod.updateStatus(status));
    }

    /**
     * Send a health check to a specific pod with exponential backoff retry logic
     * 
     * @param pod The backend pod to check
     * @return HealthCheckResponse containing the status of the health check
     */
    @Override
    public HealthCheckResponse<String> sendHealthCheck(BackendPod pod) {
        logger.debug("Sending health check to pod: {}", pod.uri());
        // probe with exponential backoff
        SuccessStatus status = BackoffServiceStandard
                .run(() -> probeService.probe(pod),
                        executorService,
                        this.healthCheckConfig.initialDelayMs(),
                        this.healthCheckConfig.maxDelayMs())
                .onRetry(() -> {
                    logger.info("Pod {} is unresponsive, marking as UNRESPONSIVE", pod.uri());
                    updatePodToStore(pod, BackendPodStatus.UNRESPONSIVE);
                }, this.healthCheckConfig.maxTries())
                .onTermination(() -> {
                    logger.warn("Pod {} has failed maximum retry attempts, marking as DEAD", pod.uri());
                    updatePodToStore(pod, BackendPodStatus.DEAD);
                })
                .onRetryCleanup(() -> {
                    logger.info("Pod {} has recovered, marking as ALIVE", pod.uri());
                    updatePodToStore(pod, BackendPodStatus.ALIVE);
                })
                .execute();

        ZonedDateTime timestamp = ZonedDateTime.now();
        switch(status) {
            case SUCCESS -> {
                logger.info("Health check successful for pod: {}", pod.uri());
                return new HealthCheckResponse<String>("success",
                        BackendPodStatus.ALIVE,
                        timestamp);
            }
            case FAIL -> {
                logger.warn("Health check failed for pod: {}", pod.uri());
                return new HealthCheckResponse<String>("dead",
                        BackendPodStatus.DEAD,
                        timestamp);
            }
        }
        logger.error("Unexpected state in health check for pod: {}", pod.uri());
        throw new IllegalStateException("Expected return from status of backoff subroutine.");
    }

    /**
     * Handle pod events from the event system
     * 
     * @param event The type of event that occurred
     * @param content The context of the event including affected pods
     */
    @Override
    public void handleEvent(BackendPodEvent event, BackendPodEventContext content) {
        logger.debug("Handling event: {} with {} affected pods", event, content.affectedPods().size());
        switch (event) {
            case ADD_POD, UPDATE_POD -> {
                logger.info("Processing {} event for {} pods", event, content.affectedPods().size());
                content.affectedPods()
                        .stream()
                        .filter(pod -> pod.status() == BackendPodStatus.ALIVE) // redundant but ensures correctness
                        .forEach(pod -> {
                            logger.debug("Scheduling health check for pod {} due to {} event", pod.uri(), event);
                            schedulePod(pod);
                        });
            }
            default -> {
                logger.debug("Ignoring event: {} as it's not relevant for health checks", event);
            }
        }
    }
}
