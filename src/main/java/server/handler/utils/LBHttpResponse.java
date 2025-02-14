package server.handler.utils;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpResponse;

public class LBHttpResponse {
    public static void handleResponse(HttpExchange exchange, HttpResponse<byte[]> response) throws IOException {
        exchange.sendResponseHeaders(response.statusCode(), response.body().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.body());
        }
    }
    public static void handleResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }


}
