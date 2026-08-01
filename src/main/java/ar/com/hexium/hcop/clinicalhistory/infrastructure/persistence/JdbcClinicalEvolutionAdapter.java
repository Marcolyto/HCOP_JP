package ar.com.hexium.hcop.clinicalhistory.infrastructure.persistence;

import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalEvolutionPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.OptionalLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Adaptador PostgreSQL que bloquea la historia durante el agregado de evolución. */
@Repository
public class JdbcClinicalEvolutionAdapter implements ClinicalEvolutionPort {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public JdbcClinicalEvolutionAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public OptionalLong append(
      long patientId, String evolutionId, String immutableEvolutionJson, long actorId, Instant occurredAt) {
    JsonNode locked = jdbc.query("""
        SELECT document_json::text
          FROM hcop_patient_documents
         WHERE patient_id = ?
         FOR UPDATE
        """, this::document, patientId).stream().findFirst().orElse(null);
    if (!(locked instanceof ObjectNode document)) return OptionalLong.empty();
    ObjectNode evolution;
    try {
      evolution = (ObjectNode) mapper.readTree(immutableEvolutionJson);
    } catch (Exception exception) {
      throw new IllegalArgumentException("La evolución serializada no es válida.", exception);
    }
    ArrayNode evolutions = document.withArray("evolutions");
    for (int index = evolutions.size() - 1; index >= 0; index--) {
      if (!evolutionId.isBlank() && evolutionId.equals(evolutions.get(index).path("id").asText(""))) {
        evolutions.remove(index);
      }
    }
    evolutions.insert(0, evolution);
    document.withObject("/meta").put("updatedAt", occurredAt.toString());
    Long revision = jdbc.queryForObject("""
        UPDATE hcop_patient_documents
           SET document_json = CAST(? AS jsonb), revision = revision + 1,
               updated_by = ?, updated_at = ?
         WHERE patient_id = ?
        RETURNING revision
        """, Long.class, document.toString(), actorId, Timestamp.from(occurredAt), patientId);
    return revision == null ? OptionalLong.empty() : OptionalLong.of(revision);
  }

  private JsonNode document(ResultSet result, int rowNumber) throws SQLException {
    return mapper.readTree(result.getString("document_json"));
  }
}
