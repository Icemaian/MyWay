package app;

import app.route.RouteService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BackendTest {
  @Test void haversineRoughlyWorks() {
    double km = RouteService.distanceKm(38.9,-77.03, 38.9,-76.9); // ~11km east
    assertTrue(km > 8 && km < 20);
  }
}

