package thread;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class OracleConnection {

    private static final String URL = "jdbc:oracle:thin:@localhost:1521/orcl"; 
    private static final String USER = "C##miniproject";  
    private static final String PASSWORD = "mini"; 

    // Get connection method
    public static Connection getConnection() {
        try {
            // Load Oracle Driver
            Class.forName("oracle.jdbc.driver.OracleDriver");

            // Connect and return
            Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Connected to Oracle Successfully!");
     
            return conn;

        } catch (ClassNotFoundException e) {
            System.out.println("Oracle JDBC Driver Missing!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Database Connection Failed!");
            e.printStackTrace();
        }
        return null;
    }
}
