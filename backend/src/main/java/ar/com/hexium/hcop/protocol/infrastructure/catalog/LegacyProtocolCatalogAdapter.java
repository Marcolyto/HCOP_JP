package ar.com.hexium.hcop.protocol.infrastructure.catalog;

import ar.com.hexium.hcop.catalog.application.port.in.LegacyProtocolCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.in.TreatmentCatalogUseCase;
import ar.com.hexium.hcop.catalog.domain.TreatmentScheme;
import ar.com.hexium.hcop.protocol.application.port.out.ProtocolCatalogPort;
import ar.com.hexium.hcop.protocol.domain.ProtocolDocument;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Encapsula el catálogo distribuido heredado detrás del puerto de Protocolos.
 */
@Component
public class LegacyProtocolCatalogAdapter implements ProtocolCatalogPort {
  private final TreatmentCatalogUseCase schemes;
  private final LegacyProtocolCatalogUseCase details;
  private final ObjectMapper mapper;

  public LegacyProtocolCatalogAdapter(
      TreatmentCatalogUseCase schemes,
      LegacyProtocolCatalogUseCase details,
      ObjectMapper mapper) {
    this.schemes = schemes;
    this.details = details;
    this.mapper = mapper;
  }

  @Override
  public List<CatalogScheme> schemes() {
    return schemes.allSchemes().stream()
        .filter(item -> !item.custom())
        .map(this::scheme)
        .toList();
  }

  @Override
  public Optional<CatalogScheme> scheme(String id) {
    return schemes.scheme(id)
        .filter(item -> !item.custom())
        .map(this::scheme);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ProtocolDocument> components(String schemeId) {
    return details.clinicalComponents(schemeId).stream()
        .map(item -> ProtocolDocument.of((Map<String, Object>) item))
        .toList();
  }

  @Override
  public void invalidate() {
    schemes.invalidate();
  }

  private CatalogScheme scheme(TreatmentScheme source) {
    @SuppressWarnings("unchecked")
    Map<String, Object> definition = mapper.convertValue(source.definition(), Map.class);
    return new CatalogScheme(
        source.id(),
        source.name(),
        source.cycleDays(),
        source.durationMinutes(),
        ProtocolDocument.of(new LinkedHashMap<>(definition)));
  }
}
