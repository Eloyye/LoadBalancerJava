package repository;

import pods.BackendPod;
import utils.EventEmitter;
import utils.EventSubscriber;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;

public class BackendPodInMemoryStore implements Repository<URI, BackendPod>, EventEmitter<EventSubscriber<BackendPodEvent, BackendPodEventContext>, BackendPodEvent, BackendPodEventContext> {

    public static BackendPodInMemoryStore inMemoryStore;
    private final Map<URI, BackendPod> uriBackendPodMap;
    private final Map<BackendPodEvent, Set<EventSubscriber<BackendPodEvent, BackendPodEventContext>>> backendPodSubscribers;

    private BackendPodInMemoryStore() {
        this.backendPodSubscribers = new HashMap<>();
        this.uriBackendPodMap = new HashMap<>();
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
        BackendPodEvent addEvent = BackendPodEvent.NEW_BACKEND;
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
        this.publish(BackendPodEvent.REMOVE_BACKEND, new BackendPodEventContext(
                BackendPodEvent.REMOVE_BACKEND,
                ZonedDateTime.now(),
                List.of(pod)
        ));
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

}
