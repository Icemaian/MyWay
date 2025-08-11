package app;

import app.db.Db;
import app.user.*;
import app.geo.NominatimGeocoder;
import app.geo.Geocoder;
import app.route.OsrmClient;
import app.route.RouteLayer;
import app.route.StopPlanner;
import app.route.StopsLayer;
import app.route.StopsLayer.StopPoint;
import app.ui.StopListPane;

import app.osm.OverpassClient;

import com.gluonhq.maps.MapView;
import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/** Minimal MyWay app (JavaFX + Gluon Maps + OSRM + Overpass + DuckDB). */
public class Main extends Application {
  // DB
  private Connection conn;
  private CarDao carDao;

  // Map & layers
  private MapView mapView;
  private PointsLayer pointsLayer;
  private RouteLayer routeLayer;
  private StopsLayer stopsLayer;
  private StopListPane stopList;

  // UI bits
  private Label routeInfo;

  // State
  private MapPoint start = new MapPoint(38.9047, -77.0164); // DC-ish fallback
  private MapPoint dest  = null;

  // Reusable clients
  private final Geocoder geocoder = new NominatimGeocoder();
  private final OsrmClient osrm   = new OsrmClient();
  private final OverpassClient overpass = new OverpassClient();

  public static void main(String[] args){ launch(args); }

  @Override public void start(Stage stage) throws Exception {
    // DB
    conn = Db.openFile("myway.duckdb");
    Db.migrate(conn);
    carDao = new CarDao(conn);

    // Top bar UI
    TextField destField = new TextField(); destField.setPromptText("Destination address…");
    Label startLbl = new Label("Current location");
    routeInfo = new Label(""); routeInfo.setStyle("-fx-text-fill:#334155; -fx-font-size:12px;");
    Button go = new Button("Go");
    Button user = new Button("👤");
    user.setOnAction(e -> openVehicleDialog(stage));

    HBox top = new HBox(8,
      styled(new HBox(new Label("Start:"), startLbl)),
      styled(new HBox(destField, go)),
      styled(user)
    );
    top.setAlignment(Pos.CENTER_LEFT);
    top.setPadding(new Insets(8));

    // Map & layers
    mapView = new MapView();
    mapView.setZoom(12);
    mapView.setCenter(start);

    routeLayer  = new RouteLayer();
    pointsLayer = new PointsLayer();
    stopsLayer  = new StopsLayer();

    mapView.addLayer(routeLayer);
    mapView.addLayer(pointsLayer);
    mapView.addLayer(stopsLayer);

    pointsLayer.setPoints(List.of(start)); // show start dot

    // Sidebar with stop list (collapsed by default)
    stopList = new StopListPane();
    stopList.setCollapsed(true);
    stopList.setOnSelect(sp -> {
      mapView.setCenter(sp.p);
      routeInfo.setText("Stop: " + sp.label);
    });

    // Stop marker clicks also update the status and center
    stopsLayer.setOnClick(sp -> {
      mapView.setCenter(sp.p);
      routeInfo.setText("Stop: " + sp.label);
    });

    // Status row with a toggle button
    Button toggleStops = new Button("Stops");
    toggleStops.setOnAction(e -> stopList.setCollapsed(stopList.isVisible())); // toggle

    HBox status = new HBox(8, routeInfo, toggleStops);
    status.setAlignment(Pos.CENTER_LEFT);
    status.setPadding(new Insets(0,8,8,8));

    BorderPane root = new BorderPane(mapView);
    root.setTop(new VBox(top, status));
    root.setRight(stopList);

    Scene scene = new Scene(root, 420, 820);
    stage.setTitle("MyWay (minimal)");
    stage.setScene(scene);
    stage.show();

    // GO button → geocode → plan stops → reroute via stops → render
    go.setOnAction(evt -> {
      String q = destField.getText();
      if (q == null || q.isBlank()) return;

      try {
        // 1) geocode destination
        var p = geocoder.geocode(q);
        dest = new MapPoint(p.lat(), p.lon());
        mapView.setCenter(dest);
        mapView.setZoom(12);

        // 2) initial OSRM route (no stops) to measure timings
        var base = osrm.route(start.getLatitude(), start.getLongitude(), dest.getLatitude(), dest.getLongitude());
        double baseKm  = base.distanceM()/1000.0;
        double baseHrs = base.durationS()/3600.0;

        // 3) choose stop targets ~ every 3h ±15m on the base route
        double[] cum = base.cumulativeSeconds();
        var idxs = StopPlanner.planStopsByTime(cum, 3.0, 15.0);

        // 4) for each target, pick a practical POI near that point (expand radius if needed)
        List<StopPoint> stops = new ArrayList<>();
        for (int idx : idxs) {
          double[] ll = base.coords().get(idx); // [lat,lon] on the base route
          var cands = overpass.findNearbyStops(ll[0], ll[1], 2500, 5);
          if (cands.isEmpty()) cands = overpass.findNearbyStops(ll[0], ll[1], 5000, 5);
          if (!cands.isEmpty()) {
            var s = cands.get(0);
            stops.add(new StopPoint(new MapPoint(s.lat(), s.lon()), s.name() + " (" + s.kind() + ")"));
          } else {
						stops.add(new StopPoint(new MapPoint(ll[0], ll[1]), "Planned stop"));
					}
        }

        // 5) re-route VIA the chosen stops so the line passes through them
        List<double[]> waypoints = new ArrayList<>();
        waypoints.add(new double[]{ start.getLatitude(), start.getLongitude() });
        for (StopPoint sp : stops) waypoints.add(new double[]{ sp.p.getLatitude(), sp.p.getLongitude() });
        waypoints.add(new double[]{ dest.getLatitude(), dest.getLongitude() });

        var withStops = (waypoints.size() > 2) ? osrm.routeVia(waypoints) : base;

        // 6) draw route polyline
        List<MapPoint> path = new ArrayList<>(withStops.coords().size());
        for (double[] ll2 : withStops.coords()) path.add(new MapPoint(ll2[0], ll2[1]));
        routeLayer.setPath(path);

        // 7) markers (start/dest)
        List<MapPoint> pts = new ArrayList<>();
        pts.add(start);
        pts.add(dest);
        pointsLayer.setPoints(pts);

        // 8) stop markers + sidebar list with when (~hour mark from base timings)
        stopsLayer.setStops(stops);
        List<String> timeLabels = new ArrayList<>();
        for (int i=0;i<stops.size();i++) {
          int idx = idxs.get(i);
          double h = cum[idx] / 3600.0;
          timeLabels.add(String.format("~%.1fh", h));
        }
        stopList.setStops(stops, timeLabels);
        stopList.setCollapsed(false);

        // 9) status line
        double showKm  = withStops.distanceM()/1000.0;
        double showHrs = withStops.durationS()/3600.0;
        routeInfo.setText(String.format("Route: %.1f km · ~%.1f h · %d stop(s)", showKm, showHrs, stops.size()));

      } catch (Exception ex) {
        new Alert(Alert.AlertType.ERROR, "Routing failed: " + ex.getMessage()).showAndWait();
      }
    });
  }

  private static Region styled(Region r){
    r.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius:14; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 12, 0, 0, 2);");
    if (r instanceof HBox h) { h.setAlignment(Pos.CENTER_LEFT); h.setPadding(new Insets(6)); }
    return r;
  }

  private void openVehicleDialog(Stage owner){
    Dialog<Void> d = new Dialog<>();
    d.initOwner(owner); d.initModality(Modality.APPLICATION_MODAL);
    d.setTitle("Vehicles");
    d.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

    TextField nick=new TextField(), make=new TextField(), model=new TextField(),
              year=new TextField(), height=new TextField(), weight=new TextField();
    nick.setPromptText("Nickname"); make.setPromptText("Make"); model.setPromptText("Model");
    year.setPromptText("Year"); height.setPromptText("Height (m)"); weight.setPromptText("Weight (kg)");

    Button add = new Button("Save");
    add.setOnAction(e -> {
      try {
        Integer y = year.getText().isBlank()?null:Integer.parseInt(year.getText().trim());
        Double h  = height.getText().isBlank()?null:Double.parseDouble(height.getText().trim());
        Double w  = weight.getText().isBlank()?null:Double.parseDouble(weight.getText().trim());
        carDao.insert(new CarProfile(0, nick.getText(), make.getText(), model.getText(), y, h, w));
        d.close();
      } catch (Exception ex) {
        new Alert(Alert.AlertType.ERROR, "Save failed: " + ex.getMessage()).showAndWait();
      }
    });

    ListView<String> list = new ListView<>();
    try {
      carDao.list().forEach(c -> list.getItems().add(c.nickname()+" · "+c.make()+" "+c.model()));
    } catch (Exception ignored) {}

    GridPane form = new GridPane();
    form.setHgap(8); form.setVgap(8); form.setPadding(new Insets(8));
    int r=0;
    form.add(new Label("Nickname"),0,r); form.add(nick,1,r++);
    form.add(new Label("Make"),0,r); form.add(make,1,r++);
    form.add(new Label("Model"),0,r); form.add(model,1,r++);
    form.add(new Label("Year"),0,r); form.add(year,1,r++);
    form.add(new Label("Height (m)"),0,r); form.add(height,1,r++);
    form.add(new Label("Weight (kg)"),0,r); form.add(weight,1,r++);
    form.add(add,1,r++);

    VBox box = new VBox(8, new Label("Your Vehicles"), list, new Separator(), new Label("Add Vehicle"), form);
    d.getDialogPane().setContent(box);
    d.showAndWait();
  }

  /** Small circle markers for start/destination. */
  private static final class PointsLayer extends MapLayer {
    private final List<MapPoint> pts = new ArrayList<>();
    public void setPoints(List<MapPoint> newPts) {
      pts.clear();
      if (newPts != null) pts.addAll(newPts);
      markDirty();
    }
    @Override protected void layoutLayer() {
      getChildren().clear();
      for (MapPoint p : pts) {
        Point2D xy = getMapPoint(p.getLatitude(), p.getLongitude());
        Circle c = new Circle(5, Color.web("#0ea5e9")); // cyan-ish
        c.setTranslateX(xy.getX());
        c.setTranslateY(xy.getY());
        getChildren().add(c);
      }
    }
  }
}

