package parallax.backend.http;

import com.sun.net.httpserver.HttpServer;
import parallax.backend.config.AppConfig;
import parallax.backend.db.InMemoryUserRepository;
import parallax.backend.db.InMemoryVehicleRepository;
import parallax.backend.db.SQLiteUserRepository;
import parallax.backend.db.SQLiteVehicleRepository;
import parallax.backend.db.UserRepository;
import parallax.backend.db.VehicleRepository;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Main entry point wiring together configuration, repositories, and HTTP handlers.
 * <p>
 * This bootstrap uses in-memory repositories for users and vehicles, registers all REST handlers
 * under the {@code /api} prefix, and starts the embedded {@link HttpServer}. When persistent
 * storage is introduced, the in-memory repositories can be swapped for SQLite-backed versions
 * without altering the routing logic.
 * </p>
 */
public class HttpServerApp {
    /**
     * Launches the HTTP server, registering routes for authentication, accounts, vehicles,
     * plate-image queries, and health checks.
     *
     * @param args ignored command-line arguments
     * @throws IOException if the server socket cannot be opened
     */
    public static void main(String[] args) throws IOException {
        AppConfig config = new AppConfig();
        // Build a DataSource using AppConfig’s JDBC URL
        org.sqlite.SQLiteDataSource ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl(config.getDatabaseUrl());

        UserRepository    userRepository    = new SQLiteUserRepository(ds);
        VehicleRepository vehicleRepository = new SQLiteVehicleRepository(ds);

        startServer(config, userRepository, vehicleRepository);
    }

    /**
     * Creates and starts the HTTP server using the provided configuration and repositories.
     *
     * @param config            configuration supplying port and external endpoints
     * @param userRepository    repository backing authentication and account operations
     * @param vehicleRepository repository backing vehicle management
     * @return the started {@link HttpServer}
     * @throws IOException if the server socket cannot be opened
     */
    public static HttpServer startServer(AppConfig config,
                                         UserRepository userRepository,
                                         VehicleRepository vehicleRepository) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
    
        server.createContext(
                "/api/health",
                new SafeHandler(new HealthHandler(), config)
        );
        server.createContext(
                "/api/auth/login",
                new SafeHandler(new AuthLoginHandler(userRepository, config), config)
        );
        server.createContext(
                "/api/auth/register",
                new SafeHandler(new AuthRegisterHandler(userRepository, config), config)
        );
        server.createContext(
                "/api/account",
                new SafeHandler(new AccountHandler(userRepository, vehicleRepository, config), config)
        );
        server.createContext(
                "/api/vehicles",
                new SafeHandler(new VehiclesHandler(vehicleRepository, userRepository, config), config)
        );
        server.createContext(
                "/api/vehicles/query-image",
                new SafeHandler(new PlateImageQueryHandler(vehicleRepository, config), config)
        );
    
        server.setExecutor(Executors.newCachedThreadPool());
    
        System.out.println("Started Parallax backend on port " + config.getPort());
        server.start();
        return server;
    }
}
