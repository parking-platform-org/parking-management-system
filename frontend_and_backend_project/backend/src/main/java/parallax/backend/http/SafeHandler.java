package parallax.backend.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import parallax.backend.config.AppConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * A protective wrapper around application-level {@link HttpHandler} instances.
 * <p>
 * SafeHandler applies two orthogonal concerns that must be enforced consistently
 * across all REST endpoints:
 * </p>
 *
 * <ul>
 *   <li><strong>CORS policy</strong> – Injects {@code Access-Control-*} headers based
 *       on the configured allowed origin in {@link AppConfig}.</li>
 *   <li><strong>Runtime safety</strong> – Captures unhandled exceptions thrown by
 *       underlying handlers and converts them into a structured HTTP 500 response,
 *       preventing connection drops or malformed upstream responses.</li>
 * </ul>
 *
 * <p>
 * This wrapper is attached in {@code HttpServerApp} when registering
 * each API route, ensuring uniform behavior without requiring every handler
 * to repeat boilerplate CORS logic or exception guards.
 * </p>
 */
public final class SafeHandler implements HttpHandler {

    private final HttpHandler delegate;
    private final AppConfig config;

    /**
     * Constructs a SafeHandler that delegates normal request processing to
     * the supplied handler while applying CORS and error-handling guarantees.
     *
     * @param delegate the underlying handler that implements the route's core logic
     * @param config   configuration source for CORS and environment-specific metadata
     */
    public SafeHandler(HttpHandler delegate, AppConfig config) {
        this.delegate = delegate;
        this.config = config;
    }

    /**
     * Handles the incoming HTTP exchange, applying:
     * <ul>
     *     <li>CORS headers for all requests</li>
     *     <li>Short-circuit handling for {@code OPTIONS} preflight requests</li>
     *     <li>Delegation to the wrapped handler for actual business logic</li>
     *     <li>Unified fallback response for uncaught exceptions</li>
     * </ul>
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            delegate.handle(exchange);

        } catch (Exception e) {
            e.printStackTrace();

            addCorsHeaders(exchange);
            byte[] body = "{\"success\":false,\"errorCode\":\"INTERNAL_SERVER_ERROR\"}"
                    .getBytes(StandardCharsets.UTF_8);

            try {
                exchange.sendResponseHeaders(500, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } catch (IOException io) {
                io.printStackTrace();
            }
        } finally {
            exchange.close();
        }
    }

    /**
     * Applies CORS headers using the origin defined in {@link AppConfig}.
     * <p>
     * This provides a centralized enforcement point so that all
     * API responses have consistent cross-origin behavior, which is required
     * when routing the frontend through a different domain or CDN layer.
     * </p>
     *
     * @param exchange active HTTP exchange receiving the headers
     */
    private void addCorsHeaders(HttpExchange exchange) {
        Headers h = exchange.getResponseHeaders();
        String origin = config.getCorsAllowedOrigin();

        h.set("Access-Control-Allow-Origin", origin);
        h.set("Access-Control-Allow-Credentials", "true");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
}

