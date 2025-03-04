package health;

import config.HealthCheckConfig;
import health.ping.backoff.BackoffServiceStandard;
import health.ping.Probeable;
import health.types.BackendPodStatus;
import health.types.HealthCheckResponse;
import health.types.HealthCheckServiceStatus;
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
    private final ExecutorService executorService;
    private final HealthCheckConfig healthCheckConfig;
    private final BackendPodInMemoryStore podStore;
    private volatile HealthCheckServiceStatus status = HealthCheckServiceStatus.RUNNING;
    private final TimeProvider timeProvider;
    private final Probeable<BackendPod> probeService;
    private final Map<BackendPod, Thread> podThreads = new ConcurrentHashMap<>();

    public HealthCheckServiceMain(ExecutorService executorService,
                                  HealthCheckConfig healthCheckConfig,
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
     *
     */
    @Override
    public void start() {
        sendAllHealthChecks();
        return;
    }

    private void sendAllHealthChecks() {
        // Send all health checks from podStore
        this.podStore.getAll().stream().filter(pod -> pod.status() == BackendPodStatus.ALIVE).forEach(this::schedulePod);
    }

    private void schedulePod(BackendPod pod) {
        startPeriodicTask(() -> sendHealthCheck(pod), pod);
    }

    private void startPeriodicTask(Supplier<HealthCheckResponse<String>> task, BackendPod pod) {
        this.executorService.submit(() -> {
            // constraint: there can only be one thread operating on a single pod
            if (podThreads.containsKey(pod)) {
                Thread inUseThread = podThreads.get(pod);
                // signal to terminate the thread
                inUseThread.interrupt();
                try {
                    inUseThread.join();
                } catch (InterruptedException e) {
                    // the thread running handles the interrupted exception
                    throw new RuntimeException(e);
                }
            }
            this.podThreads.put(pod, Thread.currentThread());
            while (!this.isStopped() && !Thread.currentThread().isInterrupted()) {
                try {
                    var response = task.get();
                    // unresponsive means terminating health checking for current thread
                    if (response.backendPodStatus() ==  BackendPodStatus.DEAD) {
                        return;
                    }
                    this.timeProvider.sleep(this.healthCheckConfig.duration());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            this.podThreads.remove(pod);
        });
    }

    public boolean isStopped() {
        return this.status == HealthCheckServiceStatus.SUSPENDED;
    }

    public void stop() {
        this.status = HealthCheckServiceStatus.SUSPENDED;
    }

    private void updatePodToStore(BackendPod pod, BackendPodStatus status) {
        this.podStore.update(pod.updateStatus(status));
    }

    /**
     * @return
     */
    @Override
    public HealthCheckResponse<String> sendHealthCheck(BackendPod pod) {
        // probe with exponential backoff
        SuccessStatus status = BackoffServiceStandard
                .run(() -> probeService.probe(pod),
                        executorService,
                        this.healthCheckConfig.initialDelayMs(),
                        this.healthCheckConfig.maxDelayMs())
                .onRetry(() -> {
                    updatePodToStore(pod, BackendPodStatus.UNRESPONSIVE);
            }, this.healthCheckConfig.maxTries())
                .onTermination(() -> {
                    updatePodToStore(pod, BackendPodStatus.DEAD);
            })
                .onRetryCleanup(() -> {
                    updatePodToStore(pod, BackendPodStatus.ALIVE);
                })
                .execute();

        switch(status) {
            case SUCCESS -> {
                return new HealthCheckResponse<String>("success",
                        BackendPodStatus.ALIVE,
                        ZonedDateTime.now());
            }
            case FAIL -> {
                return new HealthCheckResponse<String>("dead",
                        BackendPodStatus.DEAD,
                        ZonedDateTime.now());
            }
        }
        throw new IllegalStateException("Expected return from status of backoff subroutine.");
    }

    /**
     * @param event
     * @param content
     */
    @Override
    public void handleEvent(BackendPodEvent event, BackendPodEventContext content) {
        switch (event) {
            case ADD_POD, UPDATE_POD -> {
                content.affectedPods()
                        .stream()
                        .filter(pod -> pod.status() == BackendPodStatus.ALIVE) // redundant but ensures correctness
                        .forEach(this::schedulePod);
            }
        }

    }
}
