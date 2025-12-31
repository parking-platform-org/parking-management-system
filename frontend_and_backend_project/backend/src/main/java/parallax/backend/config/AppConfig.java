package parallax.backend.config;

/**
 * Application-level configuration reader for the Parallax backend.
 * <p>
 * Values are primarily resolved from environment variables, with sensible defaults
 * that allow the HTTP server, demo administrator account, and external plate
 * recognition service to function without additional setup.
 * </p>
 */

public class AppConfig {
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_DB_PATH = "db_file/parking.db";
    private static final boolean DEFAULT_ADMIN_ENABLED = true;
    private static final String DEFAULT_ADMIN_EMAIL = "admin@parallax.local";
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin1234!";
    private static final String DEFAULT_PLATE_SERVICE_BASE_URL = "http://localhost:9000";
    private static final String DEFAULT_CORS_ALLOWED_ORIGIN = "https://parallax.twilightfrosty.com";

    /**
     * Flag indicating whether the built-in administrator account is enabled. Resolved from
     * {@code PARALLAX_ADMIN_ENABLED}.
     */
    public static final boolean ADMIN_ENABLED = getBooleanEnv(
            "PARALLAX_ADMIN_ENABLED",
            DEFAULT_ADMIN_ENABLED
    );
    /**
     * Email/username for the built-in administrator account, defaulting to
     * {@value DEFAULT_ADMIN_EMAIL} unless overridden via {@code PARALLAX_ADMIN_EMAIL}.
     */
    public static final String ADMIN_EMAIL = getEnvOrDefault(
            "PARALLAX_ADMIN_EMAIL",
            DEFAULT_ADMIN_EMAIL
    );
    /**
     * Plaintext password for the built-in administrator account read from
     * {@code PARALLAX_ADMIN_PASSWORD} or defaulting to {@value DEFAULT_ADMIN_PASSWORD}.
     */
    public static final String ADMIN_PASSWORD = getEnvOrDefault(
            "PARALLAX_ADMIN_PASSWORD",
            DEFAULT_ADMIN_PASSWORD
    );

    /**
     * Base URL for the external plate-recognition microservice.
     * <p>
     * Resolved from the {@code PARALLAX_PLATE_SERVICE_BASE_URL} environment variable,
     * defaulting to {@value DEFAULT_PLATE_SERVICE_BASE_URL}. This value is consumed by
     * handlers that perform outbound HTTP requests to the Python OCR service.
     * </p>
     */
    private final String plateServiceBaseUrl = getEnvOrDefault(
            "PARALLAX_PLATE_SERVICE_BASE_URL",
            DEFAULT_PLATE_SERVICE_BASE_URL
    );

    /**
     * Allowed CORS origin for all inbound HTTP requests.
     * <p>
     * The value is taken from {@code PARALLAX_CORS_ALLOWED_ORIGIN} when present, or
     * defaults to {@value DEFAULT_CORS_ALLOWED_ORIGIN}. This config centralizes CORS
     * policy so that {@link parallax.backend.http.SafeHandler} can apply consistent
     * headers across all API endpoints without embedding hard-coded domain values
     * in application logic.
     * </p>
     */
    private final String corsAllowedOrigin = getEnvOrDefault(
            "PARALLAX_CORS_ALLOWED_ORIGIN",
            DEFAULT_CORS_ALLOWED_ORIGIN
    );

    /**
     * Returns the configured CORS-allowed origin.
     *
     * @return the origin value to be used in {@code Access-Control-Allow-Origin}
     *         for all responses that pass through {@link parallax.backend.http.SafeHandler}
     */
    public String getCorsAllowedOrigin() {
        return corsAllowedOrigin;
    }

    /**
        * Returns the port the embedded HTTP server should bind to.
        * The port is read from the {@code PARALLAX_PORT} environment variable when present;
        * otherwise the default {@value DEFAULT_PORT} is used.
        *
        * @return configured HTTP port
        */
    public int getPort() {
        String portValue = System.getenv("PARALLAX_PORT");
        if (portValue != null && !portValue.isBlank()) {
            try {
                return Integer.parseInt(portValue);
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return DEFAULT_PORT;
    }

    /**
        * Builds the JDBC URL for the SQLite database file.
        * <p>
        * The file path is taken from the {@code PARALLAX_DB_PATH} environment variable when set,
        * falling back to {@value DEFAULT_DB_PATH}. The returned URL is prefixed with
        * {@code jdbc:sqlite:} for consumption by future JDBC-based repositories.
        * </p>
        *
        * @return JDBC URL pointing to the configured database file
        */
    public String getDatabaseUrl() {
        String path = System.getenv("PARALLAX_DB_PATH");
        if (path == null || path.isBlank()) {
            path = DEFAULT_DB_PATH;
        }
        return "jdbc:sqlite:" + path;
    }

    /**
        * Returns the base URL for the external plate recognition service.
        * The value is resolved from {@code PARALLAX_PLATE_SERVICE_BASE_URL} and defaults to
        * {@value DEFAULT_PLATE_SERVICE_BASE_URL}.
        *
        * @return base URL used when invoking the Python plate detection service
        */
    public String getPlateServiceBaseUrl() {
        return plateServiceBaseUrl;
    }

    private static boolean getBooleanEnv(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
