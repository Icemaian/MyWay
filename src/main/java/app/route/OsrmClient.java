package app.route;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal OSRM client using the public demo server (hardened parsing + debug). */
public final class OsrmClient {
  private static final boolean DEBUG = Boolean.getBoolean("osrm.debug");

  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(20)).build();

  public record Route(double distanceM, double durationS, List<double[]> coords, double[] segmentDurationsS) {
    public double[] cumulativeSeconds() {
      int n = coords.size();
      double[] cum = new double[n];
      if (segmentDurationsS == null || segmentDurationsS.length == 0 || n <= 1) return cum;
      int m = Math.min(segmentDurationsS.length, n - 1);
      for (int i = 1; i <= m; i++) cum[i] = cum[i-1] + segmentDurationsS[i-1];
      return cum;
    }
  }

  /** Two-point route. */
  public Route route(double startLat, double startLon, double destLat, double destLon) throws Exception {
    var wps = List.of(new double[]{startLat,startLon}, new double[]{destLat,destLon});
    return routeVia(wps);
  }

  /** Multi-waypoint route: entries are [lat,lon]. */
  public Route routeVia(List<double[]> waypoints) throws Exception {
    if (waypoints == null || waypoints.size() < 2) throw new IllegalArgumentException("need >=2 waypoints");

    StringBuilder sb = new StringBuilder("https://router.project-osrm.org/route/v1/driving/");
    for (int i=0;i<waypoints.size();i++){
      double[] w = waypoints.get(i);
      if (i>0) sb.append(';');
      sb.append(w[1]).append(',').append(w[0]); // lon,lat
    }
    sb.append("?overview=full&geometries=geojson&steps=false&annotations=duration");

    String url = sb.toString();
    if (DEBUG) System.err.println("[OSRM] URL: " + url);

    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
        .header("User-Agent", "myway-min/1.0 (dev)")
        .timeout(Duration.ofSeconds(20))
        .build();
    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

    if (DEBUG) {
      System.err.println("[OSRM] HTTP " + resp.statusCode() + ", body length=" + resp.body().length());
      try {
        Files.createDirectories(Path.of("build"));
        Files.writeString(Path.of("build/osrm-last.json"),
            resp.body(), StandardCharsets.UTF_8);
        System.err.println("[OSRM] wrote build/osrm-last.json");
      } catch (Exception ignore) {}
    }

    if (resp.statusCode() != 200) throw new IllegalStateException("OSRM HTTP " + resp.statusCode());
    return parse(resp.body());
  }

  /** Parse distance, duration, coordinates, and per-edge durations (first route only). */
  public static Route parse(String json) {
    double dist = extractNumber(json, "\"distance\"\\s*:\\s*([0-9.Ee+-]+)");
    double dur  = extractNumber(json, "\"duration\"\\s*:\\s*([0-9.Ee+-]+)");

    // --- find the first route's geometry.coordinates array ---
    int geomKey = json.indexOf("\"geometry\"");
    if (geomKey < 0) throw new IllegalStateException("No geometry key");
    int coordKey = json.indexOf("\"coordinates\"", geomKey);
    if (coordKey < 0) throw new IllegalStateException("No coordinates key");
    int start = json.indexOf('[', coordKey);
    if (start < 0) throw new IllegalStateException("No coordinates array start");

    int depth = 0, end = -1;
    for (int i = start; i < json.length(); i++) {
      char ch = json.charAt(i);
      if (ch == '[') depth++;
      else if (ch == ']') { depth--; if (depth == 0) { end = i; break; } }
    }
    if (end < 0) throw new IllegalStateException("Unclosed coordinates array");
    String coordsSection = json.substring(start, end + 1);
    if (DEBUG) {
      System.err.println("[OSRM] coords section (first 300 chars): " +
          coordsSection.substring(0, Math.min(300, coordsSection.length())));
    }

    // [ lon , lat ] pairs — exclude commas from BOTH tokens
    Pattern pair = Pattern.compile("\\[\\s*([^\\],\\s]+)\\s*,\\s*([^\\],\\s]+)\\s*\\]");
    Matcher pm = pair.matcher(coordsSection);
    List<double[]> pts = new ArrayList<>();
    int pairCount = 0;
    while (pm.find()) {
      String sx = pm.group(1), sy = pm.group(2);
      double lon, lat;
      try {
        lon = parseNum(sx);
        lat = parseNum(sy);
      } catch (NumberFormatException nfe) {
        if (DEBUG) {
          int s = Math.max(0, pm.start()-20), e = Math.min(coordsSection.length(), pm.end()+20);
          System.err.println("[OSRM] BAD pair token lon=" + sx + " lat=" + sy);
          System.err.println("[OSRM] context: ..." + coordsSection.substring(s, e) + "...");
        }
        throw nfe;
      }
      pts.add(new double[]{lat, lon});
      pairCount++;
    }
    if (DEBUG) System.err.println("[OSRM] matched pairs: " + pairCount);
    if (pts.isEmpty()) throw new IllegalStateException("No coordinate pairs found");

    // durations from first leg's annotation.duration
    double[] seg = new double[Math.max(0, pts.size() - 1)];
    Pattern ann = Pattern.compile("\"annotation\"\\s*:\\s*\\{[^}]*?\"duration\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    Matcher am = ann.matcher(json);
    if (am.find()) {
      String arr = am.group(1);
      Matcher num = Pattern.compile("([-0-9.Ee+]+)").matcher(arr);
      int i = 0;
      while (num.find() && i < seg.length) {
        String tok = num.group(1);
        try { seg[i++] = parseNum(tok); }
        catch (NumberFormatException nfe) {
          if (DEBUG) System.err.println("[OSRM] BAD duration token: " + tok);
          throw nfe;
        }
      }
      if (DEBUG) System.err.println("[OSRM] durations parsed: " + i);
    }

    return new Route(dist, dur, pts, seg);
  }

  private static double extractNumber(String json, String pattern) {
    Matcher m = Pattern.compile(pattern).matcher(json);
    if (m.find()) {
      try { return parseNum(m.group(1)); } catch (NumberFormatException ignore) {}
    }
    return Double.NaN;
  }

  /** Strip trailing commas/whitespace and any non-numeric chars except sign, dot, exponent. */
  private static double parseNum(String s) {
    String cleaned = s.trim().replaceAll("[,]+$", "");
    cleaned = cleaned.replaceAll("[^0-9eE+\\-\\.]", "");
    return Double.parseDouble(cleaned);
  }
}

