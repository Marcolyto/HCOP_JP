package ar.com.hexium.hcop.config;

import ar.com.hexium.hcop.catalog.DiagnosisCatalogService;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
public class ClinicalCatalogBootstrap {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final DiagnosisCatalogService diagnoses;

  public ClinicalCatalogBootstrap(
      JdbcTemplate jdbc,
      ObjectMapper mapper,
      DiagnosisCatalogService diagnoses) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.diagnoses = diagnoses;
  }

  @Transactional
  public void seed() {
    Long actorId = jdbc.query("""
        SELECT id FROM local_users WHERE enabled = true ORDER BY id LIMIT 1
        """, result -> result.next() ? result.getLong(1) : null);
    if (actorId == null) return;
    seedDiagnosisSetting(actorId);
    for (var item : diagnoses.equivalences()) seedEquivalence(item, actorId);
  }

  private void seedDiagnosisSetting(long actorId) {
    var definition = mapper.createObjectNode();
    definition.put("schemaVersion", 1);
    definition.putArray("visibleSystems").add("ajcc").add("snomed").add("cie10");
    definition.put("requireAll", true);
    insertIfMissing(
        "diagnosis-setting", "diagnosis:display", "Clasificación diagnóstica",
        "Define las clasificaciones visibles y obligatorias.", definition.toString(), actorId);
  }

  private void seedEquivalence(DiagnosisCatalogService.Equivalence item, long actorId) {
    var definition = mapper.createObjectNode();
    definition.put("schemaVersion", 1);
    definition.set("ajcc", mapper.valueToTree(Map.of(
        "code", item.ajccCode(), "display", item.ajccDisplay(),
        "version", "8", "source", "AJCC 8 - catálogo TNM local")));
    definition.set("snomed", mapper.valueToTree(Map.of(
        "code", item.snomedCode(), "display", item.snomedDisplay(),
        "version", "MAIN", "source", "Catálogo terminológico local",
        "sourceConceptId", item.snomedCode())));
    definition.set("cie10", mapper.valueToTree(Map.of(
        "code", item.cie10Code(), "display", item.snomedDisplay(),
        "version", "Mapeo local", "source", "Equivalencias iniciales HCOP JP",
        "sourceConceptId", item.snomedCode())));
    definition.put("relation", item.relation());
    definition.put("confidence", "exact".equals(item.relation()) ? "high" : "medium");
    definition.put("notes", "Equivalencia inicial: debe ser confirmada por el profesional.");
    insertIfMissing(
        "diagnosis-equivalence", "diagnosis:" + item.ajccCode(), item.ajccDisplay(),
        "Equivalencia inicial editable desde Configuración.", definition.toString(), actorId);
  }

  private void insertIfMissing(
      String kind, String key, String name, String description, String definition, long actorId) {
    jdbc.update("""
        INSERT INTO clinical_configuration_items (
          item_kind, item_key, display_name, description, active, definition_json,
          revision, created_by, updated_by
        ) VALUES (?, ?, ?, ?, true, CAST(? AS jsonb), 1, ?, ?)
        ON CONFLICT (item_kind, item_key) DO NOTHING
        """, kind, key, name, description, definition, actorId, actorId);
    jdbc.update("""
        INSERT INTO clinical_configuration_versions (
          configuration_item_id, revision, display_name, description, active,
          definition_json, changed_by
        )
        SELECT id, revision, display_name, description, active, definition_json, ?
          FROM clinical_configuration_items
         WHERE item_kind = ? AND item_key = ?
        ON CONFLICT (configuration_item_id, revision) DO NOTHING
        """, actorId, kind, key);
  }
}
