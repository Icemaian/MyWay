package app.user;

import java.sql.*;
import java.util.*;

public class CarDao {
  private final Connection c;
  public CarDao(Connection c){ this.c = c; }

  public List<CarProfile> list() throws Exception {
    try (Statement s = c.createStatement();
         ResultSet rs = s.executeQuery(
           "SELECT id,nickname,make,model,year,height_m,weight_kg FROM car_profiles ORDER BY id")) {
      List<CarProfile> out = new ArrayList<>();
      while (rs.next()) out.add(row(rs));
      return out;
    }
  }

  public int insert(CarProfile p) throws Exception {
    int id;
    try (Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(id),0)+1 FROM car_profiles")) {
      rs.next(); id = rs.getInt(1);
    }
    try (PreparedStatement ps = c.prepareStatement(
      "INSERT INTO car_profiles(id,nickname,make,model,year,height_m,weight_kg) VALUES (?,?,?,?,?,?,?)")) {
      ps.setInt(1, id);
      ps.setString(2, p.nickname());
      ps.setString(3, p.make());
      ps.setString(4, p.model());
      if (p.year()!=null) ps.setInt(5, p.year()); else ps.setNull(5, Types.INTEGER);
      if (p.heightM()!=null) ps.setDouble(6, p.heightM()); else ps.setNull(6, Types.DOUBLE);
      if (p.weightKg()!=null) ps.setDouble(7, p.weightKg()); else ps.setNull(7, Types.DOUBLE);
      ps.executeUpdate();
    }
    return id;
  }

  private static CarProfile row(ResultSet rs) throws SQLException {
    return new CarProfile(
      rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
      (Integer)rs.getObject(5), (Double)rs.getObject(6), (Double)rs.getObject(7)
    );
  }
}

