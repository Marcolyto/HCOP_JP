package ar.com.hexium.hcop.catalog.application.service;

import ar.com.hexium.hcop.catalog.application.port.in.DrugCatalogUseCase;
import ar.com.hexium.hcop.catalog.application.port.out.DrugCatalogStore;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DrugCatalogApplicationService implements DrugCatalogUseCase {
  private final DrugCatalogStore store;

  public DrugCatalogApplicationService(DrugCatalogStore store) {
    this.store = store;
  }

  @Override
  public int total() {
    return store.drugs().size();
  }

  @Override
  public List<Object> search(String query) {
    String needle = normalize(query);
    List<Object> result = new ArrayList<>();
    for (Object drugObject : store.drugs()) {
      Map<?, ?> drug = (Map<?, ?>) drugObject;
      if (!needle.isBlank() && !normalize(String.join(" ",
          String.valueOf(drug.get("name")), String.valueOf(drug.get("brand")),
          String.valueOf(drug.get("presentation")))).contains(needle)) continue;
      result.add(drugObject);
      if (result.size() >= 100) break;
    }
    return result;
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ")
        .trim();
  }
}
