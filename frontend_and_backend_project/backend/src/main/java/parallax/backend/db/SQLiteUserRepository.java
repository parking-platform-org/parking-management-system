package parallax.backend.db;

import parallax.backend.model.User;

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
     *  兼容前端：支持  new SQLiteUserRepository(dataSource)
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
     *  前端需要但接口里没有的 5 个方法 —— 仅做委托
     * ------------------------------------------------------------ */

    /** 按 username 查找（前端使用） */
    public Optional<User> findByUsername(String username) {
        // 直接利用已有查询：username 与 identifier 相同语义
        return findByEmail(username)     // 可能 username == email
                .or(() -> findAllUsers().values().stream()
                        .filter(u -> u.getUsername().equals(username))
                        .findFirst());
    }

    /** email 或 phone 任意查找 */
    public Optional<User> findByEmailOrPhone(String identifier) {
        Optional<User> byEmail = findByEmail(identifier);
        return byEmail.isPresent() ? byEmail : findByPhone("", identifier);
    }

    /** 前端 admin 取所有用户 */
    public List<User> findAll() {
        return new ArrayList<>(findAllUsers().values());
    }

    /** 新增 / 更新用户 */
    public void save(User user) {
        // 简化：如果已存在就更新，否则插入
        if (findByUsername(user.getUsername()).isPresent()) {
            updateContact(user.getUsername(), user.getEmail(),
                    user.getPhoneCountry(), user.getPhone());
        } else {
            createUser(user);
        }
    }

    /** 根据 username 删除 */
    public void deleteByUsername(String username) {
        deleteUser(username);
    }

    private final Connection conn;

    public SQLiteUserRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
        ensureSchema();                    // <-- NEW
    }

    // ------------------------------------------------------------------
    // Helper: create table if it does not yet exist
    // ------------------------------------------------------------------
    private void ensureSchema() {
        DatabaseManager.ensureSchema(conn);
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
        u.setCountry(rs.getString("country"));
        u.setFirstName(rs.getString("firstName"));
        u.setLastName(rs.getString("lastName"));
        u.setBirthMonth(rs.getString("birthMonth"));
        u.setBirthDay(rs.getString("birthDay"));
        u.setBirthYear(rs.getString("birthYear"));
        u.setEmail(rs.getString("email"));
        u.setPassword(rs.getString("password"));

        u.setPhoneCountry(rs.getString("phoneCountry"));
        u.setPhone(rs.getString("phone"));

        u.setContactMethod(rs.getString("contactMethod"));
        u.setCreatedAt(rs.getString("createdAt"));
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
        String sql = "SELECT * FROM Users WHERE phoneCountry = ? AND phone = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phoneCountry);
            ps.setString(2, phoneDigits);
            return mapSingleResult(ps);
        } catch (SQLException e) {
            throw new RuntimeException("findByPhone failed: " + e.getMessage(), e);
        }
    }

    @Override
    public User createUser(User user) {
        String sql = """
            INSERT INTO Users(
                username,
                country,
                firstName,
                lastName,
                birthMonth,
                birthDay,
                birthYear,
                email,
                password,
                phoneCountry,
                phone,
                contactMethod,
                createdAt
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getCountry());
            ps.setString(3, user.getFirstName());
            ps.setString(4, user.getLastName());
            ps.setString(5, user.getBirthMonth());
            ps.setString(6, user.getBirthDay());
            ps.setString(7, user.getBirthYear());
            ps.setString(8, user.getEmail());
            ps.setString(9, user.getPassword());
            ps.setString(10, user.getPhoneCountry());
            ps.setString(11, user.getPhone());
            ps.setString(12, user.getContactMethod());
            ps.setString(13, user.getCreatedAt());
            ps.executeUpdate();
            return user;
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE")) {
                throw new IllegalStateException("Username or email already exists", e);
            }
            throw new RuntimeException("createUser failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<User> updateContact(String username,
                                        String newEmail,
                                        String phoneCountry,
                                        String phoneDigits) {
        String sql = "UPDATE Users " +
                    "SET username = ?, email = ?, phoneCountry = ?, phone = ? " +
                    "WHERE username = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newEmail); 
            ps.setString(2, newEmail); 
            ps.setString(3, phoneCountry);
            ps.setString(4, phoneDigits);
            ps.setString(5, username);

            int updated = ps.executeUpdate();
            if (updated == 0) {
                return Optional.empty();
            }
            return findByEmail(newEmail);
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
