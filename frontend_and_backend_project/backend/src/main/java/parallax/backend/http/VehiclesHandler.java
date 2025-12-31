package parallax.backend.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import parallax.backend.config.AppConfig;
import parallax.backend.db.UserRepository;
import parallax.backend.db.VehicleRepository;
import parallax.backend.model.User;
import parallax.backend.model.Vehicle;
import parallax.backend.model.VehicleWithOwner;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP handler for vehicle management endpoints.
 * <p>
 * Exposes operations for listing vehicles, registering a new vehicle, deleting a vehicle, updating
 * blacklist status, and querying by plate number. Supports both self-service actions and
 * administrative controls when the configured admin user performs the request.
 * </p>
 */
public class VehiclesHandler implements HttpHandler {
    private static final Gson gson = new Gson();
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final AppConfig appConfig;

    /**
     * Creates a handler backed by the provided repositories.
     *
     * @param vehicleRepository repository for vehicle persistence and blacklist state
     * @param userRepository    repository used to validate or seed user records
     * @param appConfig         configuration providing admin credentials and CORS settings
     */
    public VehiclesHandler(VehicleRepository vehicleRepository, UserRepository userRepository, AppConfig appConfig) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.appConfig = appConfig;
    }

    /**
     * Dispatches requests to vehicle operations based on HTTP method and path suffix.
     * Return {@code 405 Method Not Allowed} for unsupported combinations.
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        switch (method.toUpperCase()) {
            case "GET" -> {
                if (path.endsWith("/query")) {
                    handleQuery(exchange);
                } else {
                    handleGet(exchange);
                }
            }
            case "POST" -> {
                if (path.endsWith("/blacklist")) {
                    handleBlacklist(exchange);
                } else {
                    handlePost(exchange);
                }
            }
            case "DELETE" -> handleDelete(exchange);
            default -> exchange.sendResponseHeaders(405, -1);
        }
    }

    /**
     * Returns vehicles for the requesting user or, if the admin account is used, all vehicles with
     * owner details.
     */
    private void handleGet(HttpExchange exchange) throws IOException {
        String username = getQueryParam(exchange.getRequestURI(), "username");
        if (isBlank(username)) {
            sendJson(exchange, 400, Map.of("message", "USERNAME_REQUIRED"));
            return;
        }

        boolean isAdmin = isAdminUser(username);
        if (isAdmin) {
            List<VehicleWithOwner> vehicles = vehicleRepository.findAllWithOwners(userRepository);
            sendJson(exchange, 200, Map.of("vehicles", vehicles));
            return;
        }

        List<Vehicle> vehicles = vehicleRepository.findByUsername(username.toLowerCase());
        sendJson(exchange, 200, vehicles);
    }

    /**
     * Registers a new vehicle for the given user after basic validation and ensures a placeholder
     * user exists when necessary.
     */
    private void handlePost(HttpExchange exchange) throws IOException {
        Vehicle request;
        try (InputStreamReader reader =
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            request = gson.fromJson(reader, Vehicle.class);
        }

        if (request == null || isBlank(request.getUsername())) {
            sendJson(exchange, 400, Map.of("message", "USERNAME_REQUIRED"));
            return;
        }

        if (!isValidLicense(request.getLicenseNumber())) {
            sendJson(exchange, 400, Map.of("message", "INVALID_LICENSE"));
            return;
        }

        if (isBlank(request.getMake()) || isBlank(request.getModel()) || isBlank(request.getYear())) {
            sendJson(exchange, 400, Map.of("message", "VEHICLE_DETAILS_REQUIRED"));
            return;
        }

        String username = request.getUsername().toLowerCase();
        String normalizedPlate = normalizeLicense(request.getLicenseNumber());

        Optional<Vehicle> existing =
                vehicleRepository.findByUsernameAndLicense(username, normalizedPlate);
        if (existing.isPresent()) {
            sendJson(exchange, 409, Map.of("message", "LICENSE_EXISTS"));
            return;
        }

        Optional<User> user = userRepository.findByEmail(username);
        if (user.isEmpty()) {
            User placeholder = new User();
            placeholder.setUsername(username);
            placeholder.setEmail(username);
            placeholder.setDisplayName(username);
            userRepository.createUser(placeholder);
        }

        Vehicle newVehicle = new Vehicle();
        newVehicle.setUsername(username);
        newVehicle.setLicenseNumber(normalizedPlate);
        newVehicle.setMake(request.getMake());
        newVehicle.setModel(request.getModel());
        newVehicle.setYear(request.getYear());
        newVehicle.setBlacklisted(false);
        newVehicle.setCreatedAt(Instant.now().toString());

        VehicleRepository.AddVehicleResult result =
                vehicleRepository.addVehicle(newVehicle);

        switch (result) {
            case CREATED -> {
                sendJson(exchange, 201, newVehicle);
            }
            case DUPLICATE_PLATE -> {
                sendJson(exchange, 409, Map.of("message", "The license plate is already registered"));
            }
        }
    }


    /**
     * Updates blacklist status for a license plate. Only the configured admin account may invoke
     * this operation.
     */
    private void handleBlacklist(HttpExchange exchange) throws IOException {
        Vehicle request;
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            request = gson.fromJson(reader, Vehicle.class);
        }
    
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getLicenseNumber())) {
            sendJson(exchange, 400, Map.of("message", "USERNAME_AND_LICENSE_REQUIRED"));
            return;
        }
    
        String username = request.getUsername().toLowerCase();
        String normalizedPlate = normalizeLicense(request.getLicenseNumber());
    
        if (!isAdminUser(username)) {
            sendJson(exchange, 403, Map.of("message", "ADMIN_ONLY"));
            return;
        }
    
        boolean targetStatus = request.isBlacklisted();
        Optional<Vehicle> updated = vehicleRepository.updateBlacklistStatus(normalizedPlate, targetStatus);
        if (updated.isEmpty()) {
            sendJson(exchange, 404, Map.of("message", "NOT_FOUND"));
            return;
        }
    
        Vehicle vehicle = updated.get();
        Map<String, Object> response = Map.of(
                "licenseNumber", vehicle.getLicenseNumber(),
                "blacklisted", vehicle.isBlacklisted()
        );
        sendJson(exchange, 200, response);
    }


    /**
     * Deletes a vehicle registration. Admin requests remove by license globally; regular users can
     * only delete their own vehicles.
     */
    private void handleDelete(HttpExchange exchange) throws IOException {
        Vehicle request;
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            request = gson.fromJson(reader, Vehicle.class);
        }
    
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getLicenseNumber())) {
            sendJson(exchange, 400, Map.of("message", "USERNAME_AND_LICENSE_REQUIRED"));
            return;
        }
    
        String username = request.getUsername().toLowerCase();
        String normalizedPlate = normalizeLicense(request.getLicenseNumber());
    
        boolean adminRequest = isAdminUser(username);
        Optional<Vehicle> existing = adminRequest
                ? vehicleRepository.findByLicense(normalizedPlate)
                : vehicleRepository.findByUsernameAndLicense(username, normalizedPlate);
    
        if (existing.isEmpty()) {
            sendJson(exchange, 404, Map.of("message", "NOT_FOUND"));
            return;
        }
    
        if (adminRequest) {
            boolean removed = vehicleRepository.removeByLicense(normalizedPlate);
            if (!removed) {
                sendJson(exchange, 404, Map.of("message", "NOT_FOUND"));
                return;
            }
        } else {
            vehicleRepository.removeVehicle(username, normalizedPlate);
        }
        exchange.sendResponseHeaders(204, -1);
    }

    /**
     * Queries whether a license plate exists in the system and returns its blacklist status without
     * requiring authentication.
     */
    private void handleQuery(HttpExchange exchange) throws IOException {
        String license = getQueryParam(exchange.getRequestURI(), "license");
        if (isBlank(license)) {
            sendJson(exchange, 400, Map.of("success", false, "message", "LICENSE_REQUIRED"));
            return;
        }

        String normalizedLicense = normalizeLicense(license);
        Optional<Vehicle> match = vehicleRepository.findByPlate(normalizedLicense);
        if (match.isEmpty()) {
            sendJson(exchange, 200, Map.of(
                    "success", true,
                    "found", false,
                    "licenseNumber", normalizedLicense,
                    "blacklisted", false
            ));
            return;
        }

        Vehicle vehicle = match.get();
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("found", true);
        response.put("licenseNumber", vehicle.getLicenseNumber());
        response.put("blacklisted", vehicle.isBlacklisted());

        sendJson(exchange, 200, response);
    }

    private boolean isValidLicense(String licenseNumber) {
        String trimmed = normalizeLicense(licenseNumber);
        if (isBlank(trimmed)) {
            return false;
        }
        return trimmed.length() >= 1 && trimmed.length() <= 7 && trimmed.matches("[A-Z0-9-]+");
    }

    private String normalizeLicense(String licenseNumber) {
        if (licenseNumber == null) {
            return null;
        }
        return licenseNumber.trim().toUpperCase();
    }

    private String getQueryParam(URI uri, String key) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        String[] pairs = uri.getQuery().split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && key.equalsIgnoreCase(kv[0])) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isAdminUser(String username) {
        return AppConfig.ADMIN_ENABLED && appConfig.ADMIN_EMAIL.equalsIgnoreCase(username);
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        byte[] bytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
