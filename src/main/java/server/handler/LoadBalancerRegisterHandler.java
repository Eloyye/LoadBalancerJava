package server.handler;

import repository.BackendPodInMemoryStore;
import java.io.IOException;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import dto.PodRegisterRequest;
import health.types.BackendPodStatus;
import pods.BackendPod;

public class LoadBalancerRegisterHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerRegisterHandler.class);
    private final BackendPodInMemoryStore inMemoryStore;
    private final Gson parser;
    
    public LoadBalancerRegisterHandler(BackendPodInMemoryStore inMemoryStore) {
        this.inMemoryStore = inMemoryStore;
        this.parser = new Gson();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        var request = exchange.getRequestBody();
        var json = new String(request.readAllBytes());
        try {
            var pod = parser.fromJson(json, PodRegisterRequest.class);
            this.inMemoryStore.add(new BackendPod(URI.create(pod.uri()), BackendPodStatus.ALIVE));
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        } catch (JsonSyntaxException e) {
            logger.error("Invalid JSON payload", e);
            exchange.sendResponseHeaders(400, 0);
            return;
        }
    }
}
