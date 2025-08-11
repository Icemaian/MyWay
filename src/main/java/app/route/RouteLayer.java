package app.route;

import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polyline;

import java.util.ArrayList;
import java.util.List;

public class RouteLayer extends MapLayer {
  private final List<MapPoint> path = new ArrayList<>();
  private Polyline line;

  public void setPath(List<MapPoint> pts) {
    path.clear();
    if (pts != null) path.addAll(pts);
    markDirty();
  }

  @Override protected void layoutLayer() {
    getChildren().clear();
    if (path.size() < 2) return;
    line = new Polyline();
    line.setStroke(Color.web("#2563eb")); // blue
    line.setStrokeWidth(3.0);
    for (MapPoint p : path) {
      Point2D xy = getMapPoint(p.getLatitude(), p.getLongitude());
      line.getPoints().addAll(xy.getX(), xy.getY());
    }
    getChildren().add(line);
  }
}

