package app.route;

import java.util.ArrayList;
import java.util.List;

/** Pick indices near every targetHour (3h default) within a ±windowMin window. */
public final class StopPlanner {
  private StopPlanner(){}

  public static List<Integer> planStopsByTime(double[] cumulativeSeconds, double everyHours, double windowMinutes) {
    List<Integer> out = new ArrayList<>();
    if (cumulativeSeconds == null || cumulativeSeconds.length == 0) return out;
    double total = cumulativeSeconds[cumulativeSeconds.length - 1];
    double target = everyHours * 3600.0;
    double win = windowMinutes * 60.0;

    while (target < total - win) { // if trip is ~3h, we won't schedule a stop
      int bestIdx = -1;
      double bestErr = Double.POSITIVE_INFINITY;
      for (int i = 1; i < cumulativeSeconds.length-1; i++) { // avoid endpoints
        double err = Math.abs(cumulativeSeconds[i] - target);
        if (err < bestErr) { bestErr = err; bestIdx = i; }
      }
      if (bestIdx >= 0 && bestErr <= win) out.add(bestIdx);
      target += everyHours * 3600.0;
    }
    return out;
  }
}

