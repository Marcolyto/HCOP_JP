package ar.com.hexium.hcop.guide.application.port.out;

import ar.com.hexium.hcop.guide.domain.GuideMetadata;
import java.util.List;

public interface GuideMetadataPort {
  List<GuideMetadata> listAll();
}
