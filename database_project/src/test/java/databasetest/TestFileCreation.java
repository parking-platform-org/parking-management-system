package databasetest;

import org.junit.Test;
import java.io.File;

import static org.junit.Assert.assertTrue;

public class TestFileCreation {

    @Test
    public void testParkingDbExists() {
        File file = new File("db_file/parking.db");

        assertTrue("parking.db should already exist", file.exists());
    }
}
