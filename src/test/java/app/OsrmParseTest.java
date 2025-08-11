package app;

import app.route.OsrmClient;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OsrmParseTest {
  @Test void parsesFixture() {
    String json = """
      {"routes":[{"distance":1234.5,"duration":456.7,"geometry":{"coordinates":[[-77.0,38.9],[-77.01,38.91]]}}]}
      """;
    var r = OsrmClient.parse(json);
    assertEquals(1234.5, r.distanceM(), 1e-6);
    assertEquals(456.7, r.durationS(), 1e-6);
    assertEquals(2, r.coords().size());
    assertEquals(38.9, r.coords().get(0)[0], 1e-6); // lat
    assertEquals(-77.0, r.coords().get(0)[1], 1e-6); // lon
  }
}

