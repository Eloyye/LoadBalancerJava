package server;

import health.types.BackendPodStatus;
import pods.BackendPod;
import repository.BackendPodEvent;
import repository.BackendPodEventContext;
import utils.EventSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoundRobinLoadBalancer implements LoadDistributable<BackendPod>, EventSubscriber<BackendPodEvent, BackendPodEventContext> {
    private static final Logger logger = LoggerFactory.getLogger(RoundRobinLoadBalancer.class);
    private final ExecutorService executor;
    private final ConcurrentLinkedQueue<BackendPod> nodes;
    private final AtomicInteger size;

    public RoundRobinLoadBalancer(ExecutorService executor) {
        this.executor = executor;
        this.nodes = new ConcurrentLinkedQueue<>();
        this.size = new AtomicInteger(0);
    }

    @Override
    public void register(BackendPod backendPod) {
        nodes.add(backendPod);
        size.incrementAndGet();
    }

    public void remove(BackendPod backendPod) {
        if (nodes.remove(backendPod)) {
            size.decrementAndGet();
        }
    }

    @Override
    synchronized public Optional<BackendPod> next() {
        int currentSize = size.get();
        if (currentSize == 0) {
            return Optional.empty();
        }
        
        // Move head to tail for round robin behavior
        BackendPod node = nodes.poll();
        if (node != null) {
            nodes.add(node);
            return Optional.of(node);
        }
        
        return Optional.empty();
    }

    @Override
    public void handleEvent(BackendPodEvent event, BackendPodEventContext content) {
        switch (event) {
            case ADD_POD-> {
                logger.debug("Processing {} event for {} pods", event, content.affectedPods().size());
                content.affectedPods()
                        .stream()
                        .filter(pod -> pod.status() == BackendPodStatus.ALIVE) // redundant but ensures correctness
                        .forEach(pod -> {
                            logger.debug("Adding pod {} to load balancer", pod.uri());
                            this.register(pod);
                        });
            }
            case REMOVE_POD -> {
                logger.debug("Processing {} event for {} pods", event, content.affectedPods().size());
                content.affectedPods()
                        .stream()
                        .forEach(pod -> {
                            logger.debug("Removing pod {} from load balancer", pod.uri());
                            // remove with same uri
                            this.remove(pod);
                        });
            }
            default -> {
                logger.debug("Ignoring event: {} as it's not relevant for load balancing", event);
            }
        }
    }

}
