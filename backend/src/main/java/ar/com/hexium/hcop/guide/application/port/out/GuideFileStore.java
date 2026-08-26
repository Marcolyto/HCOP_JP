package ar.com.hexium.hcop.guide.application.port.out;

import ar.com.hexium.hcop.guide.domain.GuideFileName;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GuideFileStore {
  List<StoredGuide> list();

  Optional<GuideContent> open(GuideFileName name);

  StoredGuide store(GuideFileName name, InputStream content, long maximumBytes);

  record StoredGuide(GuideFileName name, long size, Instant updatedAt) {
  }

  record GuideContent(GuideFileName name, long size, InputStream content) {
  }
}
