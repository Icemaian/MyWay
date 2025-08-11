package app;

import app.db.Db;
import app.user.*;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import static org.junit.jupiter.api.Assertions.*;

public class DbTest {
  @Test void canInsertAndListCars() throws Exception {
    try (Connection c = Db.openFile("")) {
      Db.migrate(c);
      CarDao dao = new CarDao(c);
      int id = dao.insert(new CarProfile(0,"My Truck","Ford","Maverick",2024,1.88,1650.0));
      assertTrue(id > 0);
      assertFalse(dao.list().isEmpty());
    }
  }
}

