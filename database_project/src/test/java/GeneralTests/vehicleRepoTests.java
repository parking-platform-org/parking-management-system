package GeneralTests;

import model.User;
import model.Vehicle;
import org.junit.jupiter.api.Test;
import database.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class vehicleRepoTests {

    @Test
    public void test_Add_Find_Blacklist() {
        javax.sql.DataSource ds = new org.sqlite.SQLiteDataSource();
        ((org.sqlite.SQLiteDataSource) ds).setUrl("jdbc:sqlite::memory:");
        DatabaseManager.getInstance();

        SQLiteUserRepository uRepo = new SQLiteUserRepository(ds);
        SQLiteVehicleRepository vRepo = new SQLiteVehicleRepository(ds);

        // 先插入用户
        uRepo.save(new User("bob", "bob@mail", "Bob", "123"));

        // 添加车辆
        Vehicle car = new Vehicle();
        car.setUsername("bob");
        car.setLicenseNumber("ab 123 cd");
        vRepo.save(car);

        assertEquals(1, vRepo.findByOwner("bob").size());

        // 更新黑名单
        vRepo.updateBlacklist("AB123CD", true);
        Optional<Vehicle> fromDb = vRepo.findByPlateNormalized("AB123CD");
        assertTrue(fromDb.isPresent());
        assertTrue(fromDb.get().isBlacklisted());
    }
}
