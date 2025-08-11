package app.geo;

public interface Geocoder {
  record Pt(double lat, double lon) {}
  Pt geocode(String address) throws Exception;
}

