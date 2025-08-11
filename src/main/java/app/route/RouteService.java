package app.route;

import app.geo.Geocoder;

public class RouteService {
  public record Result(double startLat, double startLon, double destLat, double destLon,
                       double distanceKm, double durationH) {}

  // Haversine (km)
  public static double distanceKm(double lat1,double lon1,double lat2,double lon2){
    double R=6371.0, dLat=Math.toRadians(lat2-lat1), dLon=Math.toRadians(lon2-lon1);
    double a = Math.sin(dLat/2)*Math.sin(dLat/2) +
               Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*
               Math.sin(dLon/2)*Math.sin(dLon/2);
    return R*2*Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  }

  /** Minimal "routing": straight line, speed 55 mph (~88.5 km/h). */
  public Result route(Geocoder.Pt start, Geocoder.Pt dest){
    double km = distanceKm(start.lat(), start.lon(), dest.lat(), dest.lon());
    double durationH = km / 88.5; // ~55 mph
    return new Result(start.lat(), start.lon(), dest.lat(), dest.lon(), km, durationH);
  }
}

