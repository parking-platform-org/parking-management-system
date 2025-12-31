import database.DatabaseManager;

public class Main {

    public static void main(String[] args) {

        // --- 关键修改：添加此行以手动加载 SQLite 驱动 ---
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            // 如果驱动 Jar 包丢失或名字错误，会在这里报错
            System.err.println("Error: SQLite Driver not found!");
        }
        // ------------------------------------------------

        // ** creates the db file
        DatabaseManager.getInstance();
        System.out.println("SQLite database initialized!");
    }
}