package GeneralTests;


import database.*;
import model.User;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class userRepoTests {

    @Test
    public void test_Save_Find_Delete() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:db_file/parking.db");   // 指向同一文件
        DatabaseManager.getInstance();                 // 建表
        SQLiteUserRepository repo = new SQLiteUserRepository(ds);

        // save
        User u = new User("alice", "alice@mail", "Alice", "pwd");
        repo.save(u);

        // findByUsername
        Optional<User> fromDb = repo.findByUsername("alice");
        assertTrue(fromDb.isPresent());
        assertEquals("alice@mail", fromDb.get().getEmail());

        // delete
        repo.deleteByUsername("alice");
        assertTrue(repo.findByUsername("alice").isEmpty());
    }
}
