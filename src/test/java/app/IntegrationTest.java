package app;

import app.route.RouteService;
import app.geo.Geocoder;
import app.user.CarProfile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Minimal end-to-end: fake geocode → route straight line → ensure duration<3h15m. */
public class IntegrationTest {
  static class FakeGeocoder implements Geocoder {
    @Override public Pt geocode(String address) {
      String a = address.toLowerCase();
      if (a.contains("308 long branch ln") || a.contains("la plata")) return new Pt(38.53, -76.98);
      if (a.contains("zoo") || a.contains("3001 connecticut ave")) return new Pt(38.9296, -77.0497);
      throw new IllegalArgumentException("Unknown address: " + address);
    }
  }

  @Test void maverick_laPlata_to_dcZoo_noStopsNeeded() {
    var geocoder = new FakeGeocoder();
    var svc = new RouteService();

    var car = new CarProfile(1, "Truck","Ford","Maverick", 2024, 1.88, 1650.0);

    var start = geocoder.geocode("308 Long Branch Ln, La Plata, MD 20646");
    var dest  = geocoder.geocode("DC Zoo, Washington, DC");
    var res   = svc.route(start, dest);

    assertTrue(res.distanceKm() > 20 && res.distanceKm() < 80, "distance km reasonable");
    // 3h15m = 3.25h — this trip should be far less
    assertTrue(res.durationH() < 3.25, "no stops needed for ~1h-ish trip");

    // ensure we used the inputs
    assertEquals(38.53, res.startLat(), 0.01);
    assertEquals(-76.98, res.startLon(), 0.01);
    assertEquals(38.9296, res.destLat(), 0.01);
    assertEquals(-77.0497, res.destLon(), 0.01);
  }
}

