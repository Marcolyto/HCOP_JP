package ar.com.hexium.hcop.catalog.application.service;

import ar.com.hexium.hcop.catalog.application.port.in.AjccStagingUseCase;
import ar.com.hexium.hcop.catalog.application.port.out.AjccCatalogStore;
import ar.com.hexium.hcop.catalog.domain.AjccSite;
import ar.com.hexium.hcop.catalog.domain.AjccStagingRule;
import ar.com.hexium.hcop.catalog.domain.CatalogSearchResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class AjccStagingApplicationService implements AjccStagingUseCase {
  private final AjccCatalogStore store;

  public AjccStagingApplicationService(AjccCatalogStore store) {
    this.store = store;
  }

  @Override
  public List<AjccSiteSummary> list() {
    return sites().stream()
        .map(site -> new AjccSiteSummary(site.id(), site.name(), site.group()))
        .toList();
  }

  @Override
  public AjccSiteView detail(String id) {
    AjccSite site = required(id);
    return new AjccSiteView(site.id(), site.name(), site.edition(), site.source(), site.guideVersion(), site.axes());
  }

  @Override
  public AjccStagingOutcome stage(String id, Map<String, String> inputValues) {
    AjccSite site = required(id);
    Map<String, String> values = new java.util.LinkedHashMap<>();
    (inputValues == null ? Map.<String, String>of() : inputValues)
        .forEach((key, value) -> values.put(key, value == null ? "" : value.trim()));
    for (AjccStagingRule rule : site.rules()) {
      boolean matches = site.columns().stream().allMatch(column -> {
        String expected = rule.values().getOrDefault(column, "");
        return "ANY".equals(expected) || expected.equals(values.getOrDefault(column, ""));
      });
      if (matches) {
        return new AjccStagingOutcome(true, rule.values().getOrDefault("Stage", ""), rule.row(), List.of());
      }
    }
    List<String> missing = site.columns().stream()
        .filter(column -> values.getOrDefault(column, "").isBlank())
        .filter(column -> site.rules().stream().anyMatch(rule -> !"ANY".equals(rule.values().get(column))))
        .toList();
    return new AjccStagingOutcome(false, "", null, missing);
  }

  @Override
  public List<CatalogSearchResult> search(String query, int limit) {
    List<String> terms = CatalogTextSearch.normalizedTerms(query);
    List<CatalogSearchResult> result = new ArrayList<>();
    for (AjccSiteSummary item : list()) {
      if (!CatalogTextSearch.matchesAll(
          item.id() + " " + item.name() + " " + item.group()
              + " carcinoma tumor maligno neoplasia cáncer", terms)) continue;
      result.add(new CatalogSearchResult("AJCC", item.id(), item.name(), item.group(), "AJCC 8", "Catálogo AJCC 8 local", null));
      if (result.size() >= limit) break;
    }
    return result;
  }

  private AjccSite required(String id) {
    return sites().stream().filter(site -> site.id().equals(id == null ? "" : id.trim())).findFirst()
        .orElseThrow(() -> new CatalogFailure(CatalogFailure.Type.NOT_FOUND, "Sitio AJCC 8 no encontrado"));
  }

  private List<AjccSite> sites() {
    return store.sites().stream()
        .sorted(Comparator.comparing(AjccSite::group).thenComparing(AjccSite::name))
        .toList();
  }
}
