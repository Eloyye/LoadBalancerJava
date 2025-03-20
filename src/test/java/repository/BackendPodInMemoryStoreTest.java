package repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import health.types.BackendPodStatus;
import pods.BackendPod;
import utils.EventSubscriber;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackendPodInMemoryStoreTest {

    private BackendPodInMemoryStore store;

    @BeforeEach
    void setUp() {
        this.store = BackendPodInMemoryStore.getStore();
    }

    @AfterEach
    void cleanUp() {
        BackendPodInMemoryStore.resetInstance();
    }

    @Test
    void getStore() {
        assertNotNull(this.store);
        assertEquals(BackendPodInMemoryStore.class, this.store.getClass());
    }

    private BackendPod createBackendPod(String address) throws URISyntaxException {
        var uri = new URI(address);
        return new BackendPod(uri, BackendPodStatus.ALIVE);
    }

    @Test
    void getSuccess() {
        try {
            String stringAddress = "http://localhost:8080";
            var backendPod = createBackendPod(stringAddress);
            this.store.add(backendPod);
            var resultBackendPod = this.store.get(new URI(stringAddress));
            assertEquals(resultBackendPod, backendPod);
        } catch (URISyntaxException e) {
            fail(e);
        }
    }

    @Test
    void getFailure() {
        try {
            URI id = new URI("http://localhost:8080");
            assertNull(this.store.get(id));
        } catch (URISyntaxException e) {
            fail(e);
        }
    }

    @Test
    void getAll() {
        try {
            var backend1 = createBackendPod("http://localhost:8080");
            var backend2 = createBackendPod("http://localhost:80");
            this.store.add(backend1);
            this.store.add(backend2);
            var backends = this.store.getAll();
            assertTrue(backends.containsAll(List.of(backend1, backend2)));
        } catch (URISyntaxException e) {
            fail(e);
        }
    }

    @Test
    void add() {
        try {
            var address = "http://localhost:8080";
            var pod = createBackendPod(address);
            this.store.add(pod);
            assertNotNull(this.store.get(new URI(address)));
        } catch (URISyntaxException e) {
            fail(e);
        }
    }

    @Test
    void addInvokesEvent() {

        try {
            var subscriber = mock(EventSubscriber.class);
            var address = "http://localhost:8080";
            var pod = createBackendPod(address);
            this.store.subscribe(BackendPodEvent.ADD_POD, subscriber);
            this.store.add(pod);
            verify(subscriber, times(1)).handleEvent(eq(BackendPodEvent.ADD_POD), any(BackendPodEventContext.class));
        } catch (URISyntaxException e) {
            fail(e);
        }
    }

    @Test
    void remove() {
        try {
            String address = "http://localhost:8080";
            var uri = new URI(address);
            this.store.add(createBackendPod(address));
            assertEquals(1, this.store.getAll().size());
            assertNotNull(this.store.get(uri));
            this.store.remove(uri);
            assertEquals(0, this.store.getAll().size());
            assertNull(this.store.get(uri));
        } catch (URISyntaxException e) {
            fail(e);
        }
    }

    @Test
    void removeInvokesEvent() {

        try {
            var subscriber = mock(EventSubscriber.class);
            var address = "http://localhost:8080";
            this.store.subscribe(BackendPodEvent.REMOVE_POD, subscriber);
            var pod = createBackendPod(address);
            this.store.add(pod);
            this.store.remove(new URI(address));
            verify(subscriber, times(1)).handleEvent(eq(BackendPodEvent.REMOVE_POD), any(BackendPodEventContext.class));
        } catch (URISyntaxException e) {
            fail(e);
        }
    }

    @Test
    void subscribeAndPublish() {
        var subscriber = mock(EventSubscriber.class);
        this.store.subscribe(BackendPodEvent.ADD_POD, subscriber);
        BackendPodEventContext context = new BackendPodEventContext(BackendPodEvent.ADD_POD, ZonedDateTime.now(), List.of());
        this.store.publish(BackendPodEvent.ADD_POD, context);
        verify(subscriber, times(1)).handleEvent(BackendPodEvent.ADD_POD, context);
    }

    @Test
    void unsubscribe() {
        var subscriber = mock(EventSubscriber.class);
        this.store.subscribe(BackendPodEvent.ADD_POD, subscriber);
        BackendPodEventContext context = new BackendPodEventContext(BackendPodEvent.ADD_POD, ZonedDateTime.now(), List.of());
        this.store.publish(BackendPodEvent.ADD_POD, context);
        verify(subscriber, times(1)).handleEvent(BackendPodEvent.ADD_POD, context);
        this.store.unsubscribe(BackendPodEvent.ADD_POD, subscriber);
        BackendPodEventContext newContext = new BackendPodEventContext(BackendPodEvent.ADD_POD, ZonedDateTime.now(), List.of());
        this.store.publish(BackendPodEvent.ADD_POD, newContext);
        reset(subscriber);
        verify(subscriber, times(0)).handleEvent(BackendPodEvent.ADD_POD, newContext);
    }
}