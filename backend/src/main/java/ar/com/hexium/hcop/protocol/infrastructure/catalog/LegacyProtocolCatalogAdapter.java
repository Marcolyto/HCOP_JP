package ar.com.hexium.hcop.protocol.infrastructure.catalog;

import ar.com.hexium.hcop.catalog.LegacyProtocolCatalogService;
import ar.com.hexium.hcop.catalog.TreatmentCatalogService;
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
  private final TreatmentCatalogService schemes;
  private final LegacyProtocolCatalogService details;
  private final ObjectMapper mapper;

  public LegacyProtocolCatalogAdapter(
      TreatmentCatalogService schemes,
      LegacyProtocolCatalogService details,
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
  public List<ProtocolDocument> components(String schemeId) {
    return details.clinicalComponents(schemeId).stream()
        .map(ProtocolDocument::of)
        .toList();
  }

  @Override
  public void invalidate() {
    schemes.invalidate();
  }

  private CatalogScheme scheme(TreatmentCatalogService.Scheme source) {
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
