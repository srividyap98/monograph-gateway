package com.monograph.gateway.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

/**
 * A real HTTP server on a random port that answers every request with one configurable response,
 * optionally after a delay. For failure modes (timeouts, error statuses, bad bodies) that a mocked
 * request factory would only pretend to produce.
 */
public final class StubHttpServer implements AutoCloseable {

    private final HttpServer server;

    private volatile int status = 200;

    private volatile String body = "[]";

    private volatile Duration delay = Duration.ZERO;

    public StubHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        }
        catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        // A delayed response must not block the next request.
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            try (exchange) {
                Thread.sleep(delay);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0) {
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                }
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            catch (IOException ex) {
                // The client gave up (e.g. read timeout) and closed the connection.
            }
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public StubHttpServer respond(int status, String body) {
        this.status = status;
        this.body = body;
        this.delay = Duration.ZERO;
        return this;
    }

    public StubHttpServer delay(Duration delay) {
        this.delay = delay;
        return this;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
