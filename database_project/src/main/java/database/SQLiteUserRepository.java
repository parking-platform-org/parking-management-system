package database;

import model.User;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * SQLite-backed implementation of {@link UserRepository}.
 * <p>
 * NOTE: this implementation relies on the Users table created in {@link DatabaseManager}.
 * The expected minimal schema is:
 * <pre>
 * CREATE TABLE IF NOT EXISTS Users(
 *     id INTEGER PRIMARY KEY AUTOINCREMENT,
 *     username TEXT UNIQUE NOT NULL,
 *     password TEXT NOT NULL,
 *     phone TEXT,
 *     email TEXT
 * );
 * </pre>
 */
public class SQLiteUserRepository implements UserRepository {

    /* ------------------------------------------------------------
     *  Extra constructor for front-end: new SQLiteUserRepository(DataSource)
     * ------------------------------------------------------------ */
    public SQLiteUserRepository(DataSource ds) {
        try {
            this.conn = ds.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot get connection from DataSource", e);
        }
        ensureSchema();                    // <-- NEW
    }

    /* ------------------------------------------------------------
     *  Convenience methods required by the web front-end
     * ------------------------------------------------------------ */

    /** Find by username (web UI variant) */
    public Optional<User> findByUsername(String username) {
        // 直接利用已有查询：username 与 identifier 相同语义
        return findByEmail(username)     // 可能 username == email
                .or(() -> findAllUsers().values().stream()
                        .filter(u -> u.getUsername().equals(username))
                        .findFirst());
    }

    /** Find by either email or phone */
    public Optional<User> findByEmailOrPhone(String identifier) {
        Optional<User> byEmail = findByEmail(identifier);
        return byEmail.isPresent() ? byEmail : findByPhone("", identifier);
    }

    /** Web admin: list all users */
    public List<User> findAll() {
        return new ArrayList<>(findAllUsers().values());
    }

    /** Insert or update user */
    public void save(User user) {
        if (findByUsername(user.getUsername()).isPresent()) {
            updateContact(user.getUsername(), user.getEmail(),
                    user.getPhoneCountry(), user.getPhone());
        } else {
            createUser(user);
        }
    }

    /** Delete by username */
    public void deleteByUsername(String username) {
        deleteUser(username);
    }

    private final Connection conn;

    public SQLiteUserRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
        ensureSchema();                    // <-- NEW
    }

    // ------------------------------------------------------------------
    // Helper: ensure Users table exists
    // ------------------------------------------------------------------
    private void ensureSchema() {
        /*
         * Running this every time is cheap in SQLite because the engine
         * simply checks sqlite_master and returns if the table already exists.
         * Doing so guarantees the table is present regardless of how the
         * connection was obtained.
         */
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS Users(
                    username TEXT PRIMARY KEY,
                    password TEXT NOT NULL,
                    phone    TEXT,
                    email    TEXT UNIQUE
                );
            """);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to create or verify Users table", e);
        }
    }

    // ------------------------------------------------------------------
    // Query helpers
    // ------------------------------------------------------------------

    private Optional<User> mapSingleResult(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        // NOTE: other optional columns (firstName, etc.) can be populated here once they
        // exist in your table definition.
        return u;
    }

    // ------------------------------------------------------------------
    // Repository API implementation
    // ------------------------------------------------------------------

    @Override
    public Optional<User> findByIdentifierAndPassword(String identifier, String password) {
        String sql = """
                SELECT * FROM Users
                WHERE (username = ? OR email = ? OR phone = ?)
                  AND password = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            ps.setString(3, identifier);
            ps.setString(4, password);
            return mapSingleResult(ps);
        } catch (SQLException e) {
            throw new RuntimeException("findByIdentifierAndPassword failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM Users WHERE email = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            return mapSingleResult(ps);
        } catch (SQLException e) {
            throw new RuntimeException("findByEmail failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<User> findByPhone(String phoneCountry, String phoneDigits) {
        String fullPhone = phoneCountry + phoneDigits;
        String sql = "SELECT * FROM Users WHERE phone = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullPhone);
            return mapSingleResult(ps);
        } catch (SQLException e) {
            throw new RuntimeException("findByPhone failed: " + e.getMessage(), e);
        }
    }

    @Override
    public User createUser(User user) {
        String sql = """
                INSERT INTO Users(username, password, phone, email)
                VALUES(?,?,?,?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());

            // ----- make phone / email NULL-safe -----
            if (user.getPhone() == null) {
                ps.setNull(3, java.sql.Types.VARCHAR);   // phone -> NULL
            } else {
                ps.setString(3, user.getPhone());        // phone
            }

            if (user.getEmail() == null) {
                ps.setNull(4, java.sql.Types.VARCHAR);   // email -> NULL
            } else {
                ps.setString(4, user.getEmail());        // email
            }
            // ----------------------------------------

            ps.executeUpdate();
            return user; // caller already has reference; if generated values are needed, query again
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE")) {
                throw new IllegalStateException("Username or email already exists", e);
            }
            throw new RuntimeException("createUser failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<User> updateContact(String username, String newEmail, String phoneCountry, String phoneDigits) {
        String sql = "UPDATE Users SET email = ?, phone = ? WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newEmail);
            ps.setString(2, phoneCountry + phoneDigits);
            ps.setString(3, username);
            int updated = ps.executeUpdate();
            if (updated == 0) return Optional.empty();
            return findByEmail(newEmail); // or query by username again
        } catch (SQLException e) {
            throw new RuntimeException("updateContact failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<User> updatePassword(String username, String newPassword) {
        String sql = "UPDATE Users SET password = ? WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPassword);
            ps.setString(2, username);
            int updated = ps.executeUpdate();
            if (updated == 0) return Optional.empty();
            // Return the updated user object
            String query = "SELECT * FROM Users WHERE username = ?";
            try (PreparedStatement q = conn.prepareStatement(query)) {
                q.setString(1, username);
                return mapSingleResult(q);
            }
        } catch (SQLException e) {
            throw new RuntimeException("updatePassword failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteUser(String username) {
        String sql = "DELETE FROM Users WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("deleteUser failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, User> findAllUsers() {
        String sql = "SELECT * FROM Users";
        Map<String, User> result = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                User u = mapRow(rs);
                result.put(u.getUsername(), u);
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("findAllUsers failed: " + e.getMessage(), e);
        }
    }
}
