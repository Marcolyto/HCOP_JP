package ar.com.hexium.hcop.guide.domain;

import java.util.List;

public record GuideMetadata(
    GuideFileName fileName,
    String title,
    String category,
    String audience,
    String source,
    String version,
    List<String> tags,
    String description,
    boolean active,
    String configurationId,
    long configurationRevision) {

  public GuideMetadata {
    tags = tags == null ? List.of() : List.copyOf(tags);
  }
}
