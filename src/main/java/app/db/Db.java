package app.db;

import java.sql.*;

public final class Db {
  private Db() {}
  public static Connection openFile(String path) throws Exception {
    Class.forName("org.duckdb.DuckDBDriver");
    return DriverManager.getConnection("jdbc:duckdb:" + path);
  }
  public static void migrate(Connection c) throws Exception {
    try (Statement s = c.createStatement()) {
      s.execute("""
        CREATE TABLE IF NOT EXISTS car_profiles(
          id         INTEGER PRIMARY KEY,
          nickname   TEXT,
          make       TEXT,
          model      TEXT,
          year       INTEGER,
          height_m   DOUBLE,
          weight_kg  DOUBLE
        )
      """);
    }
  }
}

