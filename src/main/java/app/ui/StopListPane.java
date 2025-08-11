package app.ui;

import app.route.StopsLayer.StopPoint;
import com.gluonhq.maps.MapPoint;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.Consumer;

public class StopListPane extends VBox {
  private final VBox list = new VBox(6);
  private Consumer<StopPoint> onSelect;

  public StopListPane() {
    setStyle("-fx-background-color: rgba(255,255,255,0.96); -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 14, 0, 0, 2);");
    setPadding(new Insets(8));
    setSpacing(8);
    Label title = new Label("Stops");
    title.setStyle("-fx-font-weight:bold;");
    getChildren().addAll(title, new ScrollPane(list){{
      setFitToWidth(true);
      setPrefViewportHeight(300);
      setStyle("-fx-background-color:transparent;");
    }});
  }

  public void setOnSelect(Consumer<StopPoint> handler) { this.onSelect = handler; }

  public void setStops(List<StopPoint> stops, List<String> timeLabels) {
    list.getChildren().clear();
    for (int i=0;i<stops.size();i++) {
      StopPoint sp = stops.get(i);
      String when = (timeLabels != null && i < timeLabels.size()) ? timeLabels.get(i) : "";
      HBox row = new HBox(8);
      row.setAlignment(Pos.CENTER_LEFT);
      Label idx = new Label(String.valueOf(i+1));
      idx.setStyle("-fx-background-color:#0ea5e9; -fx-text-fill:white; -fx-padding:2 6; -fx-background-radius:10;");
      Label name = new Label(sp.label);
      Label t = new Label(when);
      t.setStyle("-fx-text-fill:#475569; -fx-font-size:11px;");
      Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
      Button go = new Button("View");
      go.setOnAction(e -> { if (onSelect != null) onSelect.accept(sp); });
      row.getChildren().addAll(idx, name, spacer, t, go);
      row.setStyle("-fx-padding:6; -fx-background-color:rgba(0,0,0,0.02); -fx-background-radius:8;");
      list.getChildren().add(row);
    }
  }

  /** Collapse/expand with layout participation. */
  public void setCollapsed(boolean collapsed) {
    setVisible(!collapsed);
    setManaged(!collapsed);
  }
}

