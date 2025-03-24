package repository;

import pods.BackendPod;
import utils.EventEmitter;
import utils.EventSubscriber;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import health.types.BackendPodStatus;

public class BackendPodInMemoryStore implements Repository<URI, BackendPod>, EventEmitter<EventSubscriber<BackendPodEvent, BackendPodEventContext>, BackendPodEvent, BackendPodEventContext> {
    private static final Logger logger = LoggerFactory.getLogger(BackendPodInMemoryStore.class);
    public static BackendPodInMemoryStore inMemoryStore;
    private final Map<URI, BackendPod> uriBackendPodMap;
    private final Map<BackendPodEvent, Set<EventSubscriber<BackendPodEvent, BackendPodEventContext>>> backendPodSubscribers;

    private BackendPodInMemoryStore() {
        this.backendPodSubscribers = new ConcurrentHashMap<>();
        this.uriBackendPodMap = new ConcurrentHashMap<>();
    }

    public static BackendPodInMemoryStore getStore() {
        if (inMemoryStore == null) {
            inMemoryStore = new BackendPodInMemoryStore();
        }
        return inMemoryStore;
    }

    public static void resetInstance() {
        inMemoryStore = new BackendPodInMemoryStore();
    }

    /**
     * @param id
     * @return
     */
    @Override
    public BackendPod get(URI id) {
        return uriBackendPodMap.get(id);
    }

    /**
     * @return
     */
    @Override
    public Collection<BackendPod> getAll() {
        return uriBackendPodMap.values();
    }

    /**
     *
     */
    @Override
    public void add(BackendPod item) {
        this.uriBackendPodMap.put(item.uri(), item);
        BackendPodEvent addEvent = BackendPodEvent.ADD_POD;
        BackendPodEventContext context = new BackendPodEventContext(
                addEvent,
                ZonedDateTime.now(),
                List.of(item)
        );
        this.publish(addEvent, context);
    }

    /**
     * @param id
     */
    @Override
    public void remove(URI id) {
        if (!this.uriBackendPodMap.containsKey(id)) {
            return;
        }
        var pod = this.uriBackendPodMap.get(id);
        this.uriBackendPodMap.remove(id);
        this.publish(BackendPodEvent.REMOVE_POD, new BackendPodEventContext(
                BackendPodEvent.REMOVE_POD,
                ZonedDateTime.now(),
                List.of(pod)
        ));
    }

    /**
     * @param item
     */
    @Override
    public void update(BackendPod pod) {
        if (pod.status() == BackendPodStatus.DEAD) {
            this.remove(pod.uri());
            return;
        }
        this.uriBackendPodMap.put(pod.uri(), pod);
        BackendPodEvent updateEvent = BackendPodEvent.UPDATE_POD;
        BackendPodEventContext context = new BackendPodEventContext(
                updateEvent,
                ZonedDateTime.now(),
                List.of(pod)
        );
        this.publish(updateEvent, context);
    }

    /**
     * @param event
     * @param subscriber
     * @return
     */
    @Override
    public void subscribe(BackendPodEvent event, EventSubscriber<BackendPodEvent, BackendPodEventContext> subscriber) {
        this.backendPodSubscribers.computeIfAbsent(event, _ -> new HashSet<>()).add(subscriber);
    }

    /**
     * @param event
     * @param content
     */
    @Override
    public void publish(BackendPodEvent event, BackendPodEventContext content) {
        logger.info("Publishing event: {}", event);
        if (!this.backendPodSubscribers.containsKey(event)) {
            return;
        }
        this.backendPodSubscribers.get(event).forEach(
                subscriber -> subscriber.handleEvent(event, content)
        );
    }

    @Override
    public void unsubscribe(BackendPodEvent event, EventSubscriber<BackendPodEvent, BackendPodEventContext> subscriber) {
        if (!this.backendPodSubscribers.containsKey(event) || !this.backendPodSubscribers.get(event).contains(subscriber)) {
            return;
        }
        this.backendPodSubscribers.get(event).remove(subscriber);
    }

    public void makePodReady(BackendPod pod) {
        this.uriBackendPodMap.put(pod.uri(), pod);
        this.publish(BackendPodEvent.POD_READY, new BackendPodEventContext(
                BackendPodEvent.POD_READY,
                ZonedDateTime.now(),
                List.of(pod)
        ));
    }

}
