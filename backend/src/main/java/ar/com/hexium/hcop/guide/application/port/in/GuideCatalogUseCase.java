package ar.com.hexium.hcop.guide.application.port.in;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

public interface GuideCatalogUseCase {
  List<GuideView> list(boolean includeInactive);

  GuideContent open(String rawName);

  UploadResult upload(String rawName, InputStream content, long declaredSize);

  record GuideView(
      String name,
      String title,
      String site,
      String audience,
      String source,
      String version,
      List<String> tags,
      String description,
      boolean active,
      String configurationId,
      Object configurationRevision,
      long size,
      Instant updatedAt) {
  }

  record GuideContent(String name, long size, InputStream content) {
  }

  record UploadResult(String name, long size, boolean replaced) {
  }
}
