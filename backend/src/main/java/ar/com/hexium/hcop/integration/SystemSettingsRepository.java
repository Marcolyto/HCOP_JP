package ar.com.hexium.hcop.integration;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class SystemSettingsRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;

  public SystemSettingsRepository(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
  }

  public Optional<Setting> find(String key) {
    return jdbc.query("""
        SELECT setting_key, setting_value::text, secret_value, revision
          FROM system_settings WHERE setting_key = ?
        """, (result, row) -> new Setting(
        result.getString("setting_key"),
        mapper.readTree(result.getString("setting_value")),
        result.getBytes("secret_value"),
        result.getLong("revision")), key).stream().findFirst();
  }

  public Setting upsert(String key, JsonNode value, byte[] secret, boolean preserveSecret, long actorId) {
    jdbc.update("""
        INSERT INTO system_settings (
          setting_key, setting_value, secret_value, revision, updated_by, updated_at
        ) VALUES (?, CAST(? AS jsonb), ?, 1, ?, ?)
        ON CONFLICT (setting_key) DO UPDATE SET
          setting_value = EXCLUDED.setting_value,
          secret_value = CASE WHEN ? THEN system_settings.secret_value ELSE EXCLUDED.secret_value END,
          revision = system_settings.revision + 1,
          updated_by = EXCLUDED.updated_by,
          updated_at = EXCLUDED.updated_at
        """, key, value.toString(), secret, actorId, Timestamp.from(clock.instant()), preserveSecret);
    return find(key).orElseThrow();
  }

  public record Setting(String key, JsonNode value, byte[] secret, long revision) {
  }
}
