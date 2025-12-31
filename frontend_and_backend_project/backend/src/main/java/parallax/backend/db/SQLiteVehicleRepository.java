package parallax.backend.db;

import parallax.backend.model.User;
import parallax.backend.model.Vehicle;
import parallax.backend.model.VehicleWithOwner;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * SQLite-backed implementation of {@link VehicleRepository},
 * using the "vehicles" table created in {@link DatabaseManager}
 */
public class SQLiteVehicleRepository implements VehicleRepository {

    /* ------------ 兼容前端的 DataSource 构造 ------------ */
    public SQLiteVehicleRepository(DataSource ds) {
        // Ensure that the database schema has been initialised **before**
        // we start using the connection obtained from the external DataSource
        DatabaseManager.getInstance();                 // <--- added line

        try {
            this.conn = ds.getConnection();
            // ------------------------------------------------------------------
            // Make sure the schema exists for *this* connection as well
            // ------------------------------------------------------------------
            DatabaseManager.ensureSchema(this.conn);
        } catch (SQLException e) {
            throw new RuntimeException("Cannot get connection from DataSource", e);
        }
    }

    /* ------------ 额外暴露前端要求的方法名 ------------ */

    // == 命名对齐 ==
    public List<Vehicle> findByOwner(String username) {
        return findByUsername(username);          // 直接委托旧实现
    }

    public Optional<Vehicle> findByOwnerAndPlate(String username, String plate) {
        return findByUsernameAndLicense(username, plate);
    }

    public Optional<Vehicle> findByPlateNormalized(String normalizedPlate) {
        return findByPlate(normalizedPlate);
    }

    public void save(Vehicle v) {
        addVehicle(v);
    }

    public void deleteByOwnerAndPlate(String username, String plate) {
        removeVehicle(username, plate);
    }

    public void updateBlacklist(String plate, boolean blacklisted) {
        updateBlacklistStatus(plate, blacklisted);
    }

    private final Connection conn;

    public SQLiteVehicleRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    // --------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------

    private String normalizePlate(String plateRaw) {
        return plateRaw == null ? null : plateRaw.replaceAll("\\s+", "").toUpperCase();
    }

    private Vehicle mapVehicle(ResultSet rs) throws SQLException {
        Vehicle v = new Vehicle();
        v.setUsername(rs.getString("owner"));
        v.setLicenseNumber(rs.getString("licenseNumber"));
        v.setMake(rs.getString("make"));
        v.setModel(rs.getString("model"));
        v.setYear(rs.getString("year"));
        v.setBlacklisted(rs.getInt("blacklisted") == 1);
        v.setCreatedAt(rs.getString("createdAt"));
        return v;
    }

    private Optional<Vehicle> mapSingleVehicle(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return Optional.of(mapVehicle(rs));
            return Optional.empty();
        }
    }

    // --------------------------------------------------------------
    // Repository API
    // --------------------------------------------------------------

    @Override
    public List<Vehicle> reassignVehicles(String oldUsername, String newUsername) {
        String sql = "UPDATE vehicles SET owner = ? WHERE owner = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newUsername);
            ps.setString(2, oldUsername);
            ps.executeUpdate();
            // 返回新 owner 所有车辆
            return findByUsername(newUsername);
        } catch (SQLException e) {
            throw new RuntimeException("reassignVehicles failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Vehicle> findByUsername(String username) {
        String sql = "SELECT * FROM vehicles WHERE owner = ?";
        List<Vehicle> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapVehicle(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("findByUsername failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Vehicle> findByUsernameAndLicense(String username, String licenseNumber) {
        String sql = """
                SELECT * FROM vehicles
                WHERE owner = ? AND licenseNumberNormalized = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, normalizePlate(licenseNumber));
            return mapSingleVehicle(ps);
        } catch (SQLException e) {
            throw new RuntimeException("findByUsernameAndLicense failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Vehicle> findByLicense(String licenseNumber) {
        String sql = "SELECT * FROM vehicles WHERE licenseNumber = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, licenseNumber);
            return mapSingleVehicle(ps);
        } catch (SQLException e) {
            throw new RuntimeException("findByLicense failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Vehicle> findByPlate(String licenseNumber) {
        String sql = "SELECT * FROM vehicles WHERE licenseNumberNormalized = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizePlate(licenseNumber));
            return mapSingleVehicle(ps);
        } catch (SQLException e) {
            throw new RuntimeException("findByPlate failed: " + e.getMessage(), e);
        }
    }

    @Override
    public AddVehicleResult addVehicle(Vehicle vehicle) {
        String sql = """
                INSERT INTO vehicles
                (owner, licenseNumber, licenseNumberNormalized, make, model, year, blacklisted, createdAt)
                VALUES(?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vehicle.getUsername());
            ps.setString(2, vehicle.getLicenseNumber());
            ps.setString(3, normalizePlate(vehicle.getLicenseNumber()));
            ps.setString(4, vehicle.getMake());
            ps.setString(5, vehicle.getModel());
            ps.setString(6, vehicle.getYear());
            ps.setInt(7, vehicle.isBlacklisted() ? 1 : 0);
            ps.setString(8, vehicle.getCreatedAt() != null
                    ? vehicle.getCreatedAt()
                    : String.valueOf(System.currentTimeMillis()));
            ps.executeUpdate();
            return AddVehicleResult.CREATED;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                return AddVehicleResult.DUPLICATE_PLATE;
            }
            throw new RuntimeException("addVehicle failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeVehicle(String username, String licenseNumber) {
        String sql = "DELETE FROM vehicles WHERE owner = ? AND licenseNumberNormalized = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, normalizePlate(licenseNumber));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("removeVehicle failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeVehiclesForUser(String username) {
        String sql = "DELETE FROM vehicles WHERE owner = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("removeVehiclesForUser failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean removeByLicense(String licenseNumber) {
        String sql = "DELETE FROM vehicles WHERE licenseNumberNormalized = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizePlate(licenseNumber));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("removeByLicense failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Vehicle> updateBlacklistStatus(String licenseNumber, boolean blacklisted) {
        String sql = "UPDATE vehicles SET blacklisted = ? WHERE licenseNumberNormalized = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, blacklisted ? 1 : 0);
            ps.setString(2, normalizePlate(licenseNumber));
            int updated = ps.executeUpdate();
            if (updated == 0) return Optional.empty();
            return findByPlate(licenseNumber);
        } catch (SQLException e) {
            throw new RuntimeException("updateBlacklistStatus failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<VehicleWithOwner> findAllWithOwners(UserRepository userRepository) {
        String sql = "SELECT * FROM vehicles";
        Map<String, User> users = userRepository.findAllUsers();
        List<VehicleWithOwner> result = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                VehicleWithOwner vwo = new VehicleWithOwner();
                Vehicle base = mapVehicle(rs);
                // copy base fields
                vwo.setUsername(base.getUsername());
                vwo.setLicenseNumber(base.getLicenseNumber());
                vwo.setMake(base.getMake());
                vwo.setModel(base.getModel());
                vwo.setYear(base.getYear());
                vwo.setBlacklisted(base.isBlacklisted());
                vwo.setCreatedAt(base.getCreatedAt());

                // enrich owner info
                User owner = users.get(vwo.getUsername());
                if (owner != null) {
                    vwo.setOwnerUsername(owner.getUsername());
                    vwo.setOwnerEmail(owner.getEmail());
                    vwo.setOwnerPhone(owner.getPhone());
                    vwo.setOwnerPhoneCountry(owner.getPhoneCountry());
                }
                result.add(vwo);
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("findAllWithOwners failed: " + e.getMessage(), e);
        }
    }
}
