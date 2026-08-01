package ar.com.hexium.hcop.configuration.infrastructure.persistence;

import ar.com.hexium.hcop.configuration.application.port.out.ConfigurationKeyConflictException;
import ar.com.hexium.hcop.configuration.application.port.out.ConfigurationStore;
import ar.com.hexium.hcop.configuration.domain.ConfigurationDefinition;
import ar.com.hexium.hcop.configuration.domain.ConfigurationItem;
import ar.com.hexium.hcop.configuration.domain.ConfigurationKind;
import ar.com.hexium.hcop.configuration.domain.ConfigurationVersion;
import ar.com.hexium.hcop.sharedkernel.domain.Revision;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class PostgresConfigurationStore implements ConfigurationStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;

  public PostgresConfigurationStore(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Override
  public List<ConfigurationItem> list(ConfigurationKind kind, boolean includeInactive) {
    return jdbc.query(select() + """
         WHERE item_kind = ? AND (? OR active = true)
         ORDER BY lower(display_name), id
        """, this::map, kind.externalName(), includeInactive);
  }

  @Override
  public Optional<ConfigurationItem> find(long id, ConfigurationKind kind) {
    return jdbc.query(
            select() + " WHERE id = ? AND item_kind = ?",
            this::map,
            id,
            kind.externalName())
        .stream()
        .findFirst();
  }

  @Override
  public Optional<ConfigurationItem> findByKey(ConfigurationKind kind, String key) {
    return jdbc.query(
            select() + " WHERE item_kind = ? AND item_key = ?",
            this::map,
            kind.externalName(),
            key)
        .stream()
        .findFirst();
  }

  @Override
  public List<ConfigurationVersion> versions(long itemId, ConfigurationKind kind) {
    return jdbc.query("""
        SELECT v.revision, v.display_name, v.description, v.active,
               v.definition_json::text, v.changed_by, u.display_name AS changed_by_name,
               v.created_at
          FROM clinical_configuration_versions v
          JOIN clinical_configuration_items i ON i.id = v.configuration_item_id
          LEFT JOIN local_users u ON u.id = v.changed_by
         WHERE v.configuration_item_id = ? AND i.item_kind = ?
         ORDER BY v.revision DESC
        """, this::mapVersion, itemId, kind.externalName());
  }

  @Override
  public ConfigurationItem insert(NewItem item) {
    Instant now = clock.instant();
    try {
      long id = jdbc.queryForObject("""
          INSERT INTO clinical_configuration_items (
            item_kind, item_key, display_name, description, active, definition_json,
            revision, created_by, updated_by, created_at, updated_at
          ) VALUES (?, ?, ?, NULLIF(?, ''), ?, CAST(? AS jsonb), 1, ?, ?, ?, ?)
          RETURNING id
          """, Long.class,
          item.kind().externalName(),
          item.key(),
          item.name(),
          item.description(),
          item.active(),
          json(item.definition()),
          item.actorId().value(),
          item.actorId().value(),
          java.sql.Timestamp.from(now),
          java.sql.Timestamp.from(now));
      ConfigurationItem stored = find(id, item.kind()).orElseThrow();
      storeVersion(stored, item.actorId());
      return stored;
    } catch (DuplicateKeyException duplicate) {
      throw new ConfigurationKeyConflictException(duplicate);
    }
  }

  @Override
  public Optional<ConfigurationItem> update(ItemUpdate update) {
    Instant now = clock.instant();
    try {
      int changed = jdbc.update("""
          UPDATE clinical_configuration_items
             SET item_key = ?,
                 display_name = ?,
                 description = NULLIF(?, ''),
                 active = ?,
                 definition_json = CAST(? AS jsonb),
                 revision = revision + 1,
                 updated_by = ?,
                 updated_at = ?
           WHERE id = ? AND item_kind = ? AND revision = ?
          """,
          update.key(),
          update.name(),
          update.description(),
          update.active(),
          json(update.definition()),
          update.actorId().value(),
          java.sql.Timestamp.from(now),
          update.id(),
          update.kind().externalName(),
          update.expectedRevision().value());
      if (changed == 0) return Optional.empty();
      ConfigurationItem stored = find(update.id(), update.kind()).orElseThrow();
      storeVersion(stored, update.actorId());
      return Optional.of(stored);
    } catch (DuplicateKeyException duplicate) {
      throw new ConfigurationKeyConflictException(duplicate);
    }
  }

  private void storeVersion(ConfigurationItem item, UserId actorId) {
    jdbc.update("""
        INSERT INTO clinical_configuration_versions (
          configuration_item_id, revision, display_name, description, active,
          definition_json, changed_by, created_at
        ) VALUES (?, ?, ?, NULLIF(?, ''), ?, CAST(? AS jsonb), ?, ?)
        """,
        item.id(),
        item.revision().value(),
        item.name(),
        item.description(),
        item.active(),
        json(item.definition()),
        actorId.value(),
        java.sql.Timestamp.from(item.updatedAt()));
  }

  private String select() {
    return """
        SELECT id, item_kind, item_key, display_name, description, active,
               definition_json::text, revision, created_at, updated_at
          FROM clinical_configuration_items
        """;
  }

  private ConfigurationItem map(ResultSet result, int row) throws SQLException {
    return new ConfigurationItem(
        result.getLong("id"),
        kind(result.getString("item_kind")),
        result.getString("item_key"),
        result.getString("display_name"),
        text(result, "description"),
        result.getBoolean("active"),
        definition(result.getString("definition_json")),
        new Revision(result.getLong("revision")),
        result.getTimestamp("created_at").toInstant(),
        result.getTimestamp("updated_at").toInstant());
  }

  private ConfigurationVersion mapVersion(ResultSet result, int row) throws SQLException {
    return new ConfigurationVersion(
        new Revision(result.getLong("revision")),
        result.getString("display_name"),
        text(result, "description"),
        result.getBoolean("active"),
        definition(result.getString("definition_json")),
        UserId.of(result.getLong("changed_by")),
        text(result, "changed_by_name"),
        result.getTimestamp("created_at").toInstant());
  }

  private ConfigurationDefinition definition(String json) {
    return ConfigurationDefinition.of(mapper.convertValue(mapper.readTree(json), Object.class));
  }

  private String json(ConfigurationDefinition definition) {
    return mapper.valueToTree(definition.value()).toString();
  }

  private ConfigurationKind kind(String value) {
    return ConfigurationKind.fromExternalName(value)
        .orElseThrow(() -> new IllegalStateException("Tipo de configuración persistido desconocido: " + value));
  }

  private String text(ResultSet result, String field) throws SQLException {
    String value = result.getString(field);
    return value == null ? "" : value;
  }
}
