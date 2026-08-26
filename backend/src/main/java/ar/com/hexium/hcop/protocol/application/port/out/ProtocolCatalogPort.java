package ar.com.hexium.hcop.protocol.application.port.out;

import ar.com.hexium.hcop.protocol.domain.ProtocolDocument;
import java.util.List;
import java.util.Optional;

/**
 * Catálogo externo de esquemas que puede alimentar protocolos clínicos.
 */
public interface ProtocolCatalogPort {
  List<CatalogScheme> schemes();

  Optional<CatalogScheme> scheme(String id);

  List<ProtocolDocument> components(String schemeId);

  void invalidate();

  record CatalogScheme(
      String id,
      String name,
      int cycleDays,
      Integer durationMinutes,
      ProtocolDocument definition) {
  }
}
