package ar.com.hexium.hcop.catalog.application.service;

import ar.com.hexium.hcop.catalog.application.port.in.TreatmentCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.out.TreatmentSchemeStore;
import ar.com.hexium.hcop.catalog.domain.TreatmentScheme;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TreatmentCatalogApplicationService implements TreatmentCatalogUseCase {
  private final TreatmentSchemeStore store;

  public TreatmentCatalogApplicationService(TreatmentSchemeStore store) {
    this.store = store;
  }

  @Override
  public List<TreatmentScheme> schemes(String query) {
    String normalized = normalize(query);
    if (normalized.isBlank()) return store.load();
    return store.load().stream().filter(scheme -> normalize(scheme.name()).contains(normalized)).toList();
  }

  @Override
  public Optional<TreatmentScheme> scheme(String id) {
    return store.load().stream().filter(scheme -> scheme.id().equals(id)).findFirst();
  }

  @Override
  public List<TreatmentScheme> allSchemes() {
    return store.load();
  }

  @Override
  public void invalidate() {
    store.invalidate();
  }

  private String normalize(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim();
  }
}
