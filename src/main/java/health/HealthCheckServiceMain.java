package health;

import config.HealthCheckConfig;
import health.ping.backoff.BackoffServiceStandard;
import health.ping.HealthCheckPingFactory;
import health.ping.Pingable;
import pods.BackendPod;
import repository.BackendPodInMemoryStore;
import utils.SuccessStatus;
import utils.network.NETWORK;
import utils.time.TimeProvider;

import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;

public class HealthCheckServiceMain implements HealthCheckService<BackendPod> {
    private final ExecutorService executorService;
    private final HealthCheckConfig healthCheckConfig;
    private final BackendPodInMemoryStore podStore;
    private volatile HealthCheckServiceStatus status = HealthCheckServiceStatus.RUNNING;
    private final TimeProvider timeProvider;

    public HealthCheckServiceMain(ExecutorService executorService,
                                  HealthCheckConfig healthCheckConfig,
                                  BackendPodInMemoryStore podStore,
                                  TimeProvider timeProvider
                                  ) {
        this.executorService = executorService;
        this.healthCheckConfig = healthCheckConfig;
        this.podStore = podStore;
        this.timeProvider = timeProvider;
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
        startPeriodicTask(() -> sendHealthCheck(pod));
    }

    private void startPeriodicTask(Runnable task) {
        this.executorService.submit(() -> {
            while (this.isStopped() && !Thread.currentThread().isInterrupted()) {
                try {
                    task.run();
                    this.timeProvider.sleep(this.healthCheckConfig.duration());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
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
        Pingable<BackendPod> client = HealthCheckPingFactory.create(NETWORK.HTTP)
                .timeout(this.healthCheckConfig.timeout())
                .executor(this.executorService)
                .build();


        //        requires timeout exception
        SuccessStatus status = BackoffServiceStandard
                .run(() -> client.ping(pod),
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


}
