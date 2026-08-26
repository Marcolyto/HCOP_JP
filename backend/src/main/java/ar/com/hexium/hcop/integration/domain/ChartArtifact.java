package ar.com.hexium.hcop.integration.domain;

import java.util.List;

public record ChartArtifact(
    String title, String chartType, String xLabel, List<Series> series) implements ChatArtifact {

  public record Series(String name, String color, List<Point> points) {
  }

  public record Point(String x, double y, String label) {
  }
}
