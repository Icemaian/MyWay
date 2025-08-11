package app.geo;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.regex.*;

public class NominatimGeocoder implements Geocoder {
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

  @Override public Pt geocode(String addr) throws Exception {
    String url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=" +
      java.net.URLEncoder.encode(addr, java.nio.charset.StandardCharsets.UTF_8);
    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
      .header("User-Agent","myway-min/1.0 (testing)")
      .timeout(Duration.ofSeconds(6)).build();
    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode()!=200) throw new IllegalStateException("HTTP "+resp.statusCode());
    // naive parse: extract first "lat":"...","lon":"..."
    Pattern pLat = Pattern.compile("\"lat\"\\s*:\\s*\"([^\"]+)\"");
    Pattern pLon = Pattern.compile("\"lon\"\\s*:\\s*\"([^\"]+)\"");
    Matcher m1 = pLat.matcher(resp.body()), m2 = pLon.matcher(resp.body());
    if (m1.find() && m2.find())
      return new Pt(Double.parseDouble(m1.group(1)), Double.parseDouble(m2.group(1)));
    throw new IllegalStateException("No result");
  }
}

