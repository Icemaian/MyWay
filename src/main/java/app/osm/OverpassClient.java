package app.osm;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

/** Overpass client to find nearby stop candidates around a location (robust parsing + debug). */
public class OverpassClient {
  private static final boolean DEBUG = Boolean.getBoolean("overpass.debug");
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();

  public record Poi(String name, String kind, double lat, double lon) {}

  /** Return up to 'limit' POIs within 'radiusMeters', sorted by distance to (lat,lon). */
  public List<Poi> findNearbyStops(double lat, double lon, int radiusMeters, int limit) throws Exception {
    String q = """
      [out:json][timeout:12];
      (
        node["amenity"~"^(fuel|restaurant|fast_food|cafe|rest_area|parking|charging_station)$"](around:RADIUS,LAT,LON);
      );
      out 50;
      """.replace("RADIUS", String.valueOf(radiusMeters))
         .replace("LAT", String.valueOf(lat))
         .replace("LON", String.valueOf(lon));

    String body = "data=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
    HttpRequest req = HttpRequest.newBuilder(URI.create("https://overpass-api.de/api/interpreter"))
        .header("Content-Type","application/x-www-form-urlencoded")
        .header("User-Agent","myway-min/1.0 (dev)")
        .timeout(Duration.ofSeconds(20))
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (DEBUG) {
      Files.createDirectories(Path.of("build"));
      Files.writeString(Path.of("build/overpass-last.json"), resp.body(), StandardCharsets.UTF_8);
      System.err.println("[OVERPASS] HTTP " + resp.statusCode() + " wrote build/overpass-last.json");
    }
    if (resp.statusCode() != 200) throw new IllegalStateException("Overpass HTTP " + resp.statusCode());

    List<Poi> all = parse(resp.body());
    all.sort(Comparator.comparingDouble(p -> hav(lat, lon, p.lat, p.lon)));
    if (all.size() > limit) return all.subList(0, limit);
    return all;
  }

  /** Robustly parse Overpass JSON nodes into POIs (no external JSON lib). */
  static List<Poi> parse(String json) {
    List<Poi> out = new ArrayList<>();
    // Iterate node objects
    Matcher node = Pattern.compile("\\{\\s*\"type\"\\s*:\\s*\"node\".*?\\}", Pattern.DOTALL).matcher(json);
    while (node.find()) {
      String e = node.group();

      Double lat = num(e, "\"lat\"\\s*:\\s*([^,\\s\\}]+)");
      Double lon = num(e, "\"lon\"\\s*:\\s*([^,\\s\\}]+)");
      if (lat == null || lon == null) continue;

      String name = str(e, "\"name\"\\s*:\\s*\"([^\"]*)\"");
      if (name == null || name.isBlank()) name = "Stop";
      String amen = str(e, "\"amenity\"\\s*:\\s*\"([^\"]*)\"");
      if (amen == null || amen.isBlank()) amen = "amenity";

      out.add(new Poi(name, amen, lat, lon));
    }
    return out;
  }

  private static String str(String s, String pat) {
    Matcher m = Pattern.compile(pat).matcher(s);
    return m.find() ? m.group(1) : null;
  }
  private static Double num(String s, String pat) {
    Matcher m = Pattern.compile(pat).matcher(s);
    if (!m.find()) return null;
    return parseNum(m.group(1));
  }
  /** Strip trailing commas and non-numeric chars except sign/dot/exponent. */
  private static double parseNum(String tok) {
    String cleaned = tok.trim().replaceAll("[,]+$", "");
    cleaned = cleaned.replaceAll("[^0-9eE+\\-\\.]", "");
    return Double.parseDouble(cleaned);
  }

  private static double hav(double lat1,double lon1,double lat2,double lon2){
    double R=6371000, dLat=Math.toRadians(lat2-lat1), dLon=Math.toRadians(lon2-lon1);
    double a = Math.sin(dLat/2)*Math.sin(dLat/2) +
               Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*
               Math.sin(dLon/2)*Math.sin(dLon/2);
    return R*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
  }
}

