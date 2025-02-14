package server.handler;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import pods.BackendPod;
import server.RoundRobinLoadBalancer;
import server.handler.utils.LBHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;
import java.io.OutputStream;


public class RootHandler implements HttpHandler {
    private final HttpClient httpClient;
    private RoundRobinLoadBalancer loadBalancer;

    public RootHandler(HttpClient httpClient, RoundRobinLoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        this.httpClient = httpClient;
    }

    /**
     * Handle the given request and generate an appropriate response.
     * See {@link HttpExchange} for a description of the steps
     * involved in handling an exchange.
     *
     * @param exchange the exchange containing the request from the
     *                 client and used to send the response
     * @throws NullPointerException if exchange is {@code null}
     * @throws IOException          if an I/O error occurs
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            BackendPod nextPod = this.loadBalancer.next();
            String targetUrl = nextPod.uri() + exchange.getRequestURI().getPath();
            if (exchange.getRequestURI().getQuery() != null) {
                targetUrl += "?" + exchange.getRequestURI().getQuery();
            }
            URI uri = URI.create(targetUrl);
            System.out.println(uri);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .method(exchange.getRequestMethod(), getRequestBody(exchange));

            // Copy original headers
            Headers originalHeaders = exchange.getRequestHeaders();
            originalHeaders.forEach((key, values) -> {
                // Skip hop-by-hop headers
                if (!isHopByHopHeader(key) && !isRestrictedHeader(key)) {
    //                suspends for some reason
                    values.forEach(value -> {
                        requestBuilder.header(key, value);
                    });
                }
            });

            var response = httpClient.sendAsync(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            HttpResponse<byte[]> resp = null;
            resp = response.get(3, TimeUnit.SECONDS);

            resp.headers().map().forEach((key, values) -> {
                if (!isHopByHopHeader(key)) {
                    values.forEach(value -> exchange.getResponseHeaders().add(key, value));
                }
            });
            LBHttpResponse.handleResponse(exchange, resp);
        } catch (Exception e) {
            // Log the error
            e.printStackTrace();

            // Send an error response to the client
            try {
                String errorMessage = "Internal Server Error";
                exchange.sendResponseHeaders(500, errorMessage.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorMessage.getBytes());
                }
            } catch (IOException responseError) {
                // Log any errors that occur while sending the error response
                responseError.printStackTrace();
            } finally {
                exchange.close();
            }
        }
    }
    private HttpRequest.BodyPublisher getRequestBody(HttpExchange exchange) throws IOException {
        // If no body, return empty
        if (exchange.getRequestBody().available() == 0) {
            return HttpRequest.BodyPublishers.noBody();
        }

        // Read the body
        byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
        return HttpRequest.BodyPublishers.ofByteArray(bodyBytes);
    }

    private boolean isRestrictedHeader(String headerName) {
        String lowerHeader = headerName.toLowerCase();
        return lowerHeader.equals("host") ||
                lowerHeader.equals("connection") ||
                lowerHeader.equals("content-length") ||
                lowerHeader.equals("expect") ||
                lowerHeader.equals("upgrade") ||
                lowerHeader.equals("cookie");
    }

    private boolean isHopByHopHeader(String headerName) {
        String lowerHeader = headerName.toLowerCase();
        return lowerHeader.equals("connection") ||
                lowerHeader.equals("keep-alive") ||
                lowerHeader.equals("proxy-authenticate") ||
                lowerHeader.equals("proxy-authorization") ||
                lowerHeader.equals("te") ||
                lowerHeader.equals("trailer") ||
                lowerHeader.equals("transfer-encoding") ||
                lowerHeader.equals("upgrade");
    }
}
