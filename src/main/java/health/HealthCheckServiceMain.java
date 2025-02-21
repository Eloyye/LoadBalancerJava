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

import java.net.http.HttpClient;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;

public class HealthCheckServiceMain implements HealthCheckService<BackendPod> {
    private final ExecutorService executorService;
    private final HealthCheckConfig healthCheckConfig;
    private final BackendPodInMemoryStore podStore;
    private final HttpClient httpClient;
    private volatile HealthCheckServiceStatus status = HealthCheckServiceStatus.RUNNING;
    private final TimeProvider timeProvider;

    public HealthCheckServiceMain(ExecutorService executorService,
                                  HealthCheckConfig healthCheckConfig,
                                  BackendPodInMemoryStore podStore,
                                  HttpClient httpClient,
                                  TimeProvider timeProvider
                                  ) {
        this.executorService = executorService;
        this.healthCheckConfig = healthCheckConfig;
        this.podStore = podStore;
        this.httpClient = httpClient;
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
        this.podStore.getAll().stream().filter(pod -> pod.status() == HealthCheckStatus.ALIVE).forEach(this::schedulePod);
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
                .run(() -> client.ping(pod))
                .onRetry(() -> {
                var newPod = new BackendPod(pod.uri(), pod.reviveAttempts() + 1, HealthCheckStatus.UNRESPONSIVE);
                this.podStore.update(newPod);
            }, this.healthCheckConfig.maxTries())
                .onTermination(() -> {
                var newPod = new BackendPod(pod.uri(), pod.reviveAttempts() + 1, HealthCheckStatus.DEAD);
                this.podStore.update(newPod);
            }).execute();

        switch(status) {
            case SUCCESS -> {
                return new HealthCheckResponse<String>("success",
                        HealthCheckStatus.ALIVE,
                        ZonedDateTime.now());
            }
            case FAIL -> {
                return new HealthCheckResponse<String>("dead",
                        HealthCheckStatus.DEAD,
                        ZonedDateTime.now());
            }
        }
        throw new IllegalStateException("Expected return from status of backoff subroutine.");
    }


}
