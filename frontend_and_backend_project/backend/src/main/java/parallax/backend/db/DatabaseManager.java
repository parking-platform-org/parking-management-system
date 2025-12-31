package parallax.backend.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public class DatabaseManager {

    private static final String DB_DIR  = "db_file";
    private static final String DB_URL  = "jdbc:sqlite:" + DB_DIR + "/parking.db";

    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {
        loadDriver();          // <-- make sure driver is registered
        createDbDirectory();   // <-- guarantee directory exists
        connect();
        createSchema(connection);   // ensure tables for the default connection
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    private void connect() {
        try {
            this.connection = DriverManager.getConnection(DB_URL);
            // The DataSource may point to a fresh (in-memory / test) DB.
            // Make sure its schema exists before any SQL is executed.
            DatabaseManager.ensureSchema(this.connection);
        } catch (SQLException e) {
            /*
             * Preserve the original SQLException message (e.getMessage()),
             * otherwise debugging becomes very hard.
             */
            throw new RuntimeException(
                    "Database connection failed: " + e.getMessage(), e);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    /**
     * Creates or upgrades all tables on the given connection.
     * The method is <i>idempotent</i>: running it multiple times is safe.
     */
    public static void ensureSchema(Connection cn) {
        createSchema(cn);
    }

    /* -----------------------------------------------------------------
     *  Internal helper – actual DDL statements (shared by both callers)
     * ----------------------------------------------------------------- */
    private static void createSchema(Connection connection) {
        try (Statement stmt = connection.createStatement()) {

            /*
             * ------------------------------------------------------------------
             * 1. Users table (authoritative definition)
             * ------------------------------------------------------------------
             */
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS Users (
                    username TEXT PRIMARY KEY,
                    country TEXT,
                    firstName TEXT,
                    lastName TEXT,
                    birthMonth TEXT,
                    birthDay TEXT,
                    birthYear TEXT,
                    email TEXT UNIQUE,
                    password TEXT,
                    phoneCountry TEXT,
                    phone TEXT,                --  << now nullable >>
                    contactMethod TEXT,
                    createdAt TEXT
                );
            """);

            /*
             * ------------------------------------------------------------------
             * 2. vehicles 表
             *    - owner 外键指向 users.username
             *    - licenseNumberNormalized 一律存大写、去空格以便全局查询
             * ------------------------------------------------------------------
             */
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS vehicles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner TEXT NOT NULL,
                    licenseNumber TEXT NOT NULL,
                    licenseNumberNormalized TEXT NOT NULL,
                    make TEXT,
                    model TEXT,
                    year TEXT,
                    blacklisted INTEGER DEFAULT 0,
                    createdAt TEXT,
                    CONSTRAINT fk_owner FOREIGN KEY(owner) REFERENCES users(username),
                    UNIQUE(owner, licenseNumber) ON CONFLICT REPLACE,
                    UNIQUE(licenseNumberNormalized)
                );
            """);

            /*
             * 旧版 Plates / Payments / Blacklist / DetectionLogs 表如果
             * 仍在其他模块使用，可以考虑保留；此处示例直接删除：
             */
            stmt.execute("DROP TABLE IF EXISTS Plates;");
            stmt.execute("DROP TABLE IF EXISTS Payments;");
            stmt.execute("DROP TABLE IF EXISTS Blacklist;");
            stmt.execute("DROP TABLE IF EXISTS DetectionLogs;");

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // -----------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------
    private void loadDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "SQLite JDBC driver not found on class-path.", e);
        }
    }

    private void createDbDirectory() {
        try {
            Path dir = Path.of(DB_DIR);
            if (Files.notExists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to create database directory '" + DB_DIR + "'.", e);
        }
    }
}
