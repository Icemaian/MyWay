package app.route;

import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import javafx.geometry.Point2D;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StopsLayer extends MapLayer {
  public static final class StopPoint {
    public final MapPoint p; public final String label;
    public StopPoint(MapPoint p, String label){ this.p=p; this.label=label; }
  }

  private final List<StopPoint> pts = new ArrayList<>();
  private Consumer<StopPoint> onClick;

  public void setStops(List<StopPoint> stops) {
    pts.clear();
    if (stops != null) pts.addAll(stops);
    markDirty();
  }

  /** Optional: caller can receive click events on a stop marker. */
  public void setOnClick(Consumer<StopPoint> handler) { this.onClick = handler; }

  @Override protected void layoutLayer() {
    getChildren().clear();
    for (StopPoint sp : pts) {
      Point2D xy = getMapPoint(sp.p.getLatitude(), sp.p.getLongitude());
      Circle c = new Circle(5, Color.web("#f97316")); // orange dot
      c.setTranslateX(xy.getX());
      c.setTranslateY(xy.getY());
      Tooltip.install(c, new Tooltip(sp.label));      // hover (if supported)
      c.setOnMouseClicked(e -> {                      // click works everywhere
        if (onClick != null) onClick.accept(sp);
        e.consume();
      });
      getChildren().add(c);
    }
  }
}

