package ar.com.hexium.hcop.integration.infrastructure.persistence;

import ar.com.hexium.hcop.integration.application.port.out.LlmConfigurationStore;
import ar.com.hexium.hcop.integration.domain.LlmConfiguration;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Repository
public class PostgresLlmConfigurationStore implements LlmConfigurationStore {
  private static final String KEY = "llm";
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final SecretBox secrets;

  public PostgresLlmConfigurationStore(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock, SecretBox secrets) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
    this.secrets = secrets;
  }

  @Override
  public LlmConfiguration find() {
    return findRow().map(this::toConfiguration).orElseGet(() -> toConfiguration(null));
  }

  @Override
  public LlmConfiguration upsert(LlmConfiguration value, boolean preserveApiKey, long actorId) {
    ObjectNode json = mapper.createObjectNode();
    json.put("enabled", value.enabled());
    json.put("provider", value.provider());
    json.put("baseUrl", value.baseUrl());
    json.put("model", value.model());
    json.put("temperature", value.temperature());
    json.put("maxTokens", value.maxTokens());
    json.put("timeoutMs", value.timeoutMs());
    byte[] encrypted = preserveApiKey ? null : secrets.encrypt(value.apiKey());
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
        """, KEY, json.toString(), encrypted, actorId, Timestamp.from(clock.instant()), preserveApiKey);
    return find();
  }

  private Optional<Row> findRow() {
    return jdbc.query("""
        SELECT setting_value::text, secret_value FROM system_settings WHERE setting_key = ?
        """, (result, row) -> new Row(mapper.readTree(result.getString(1)), result.getBytes(2)), KEY)
        .stream().findFirst();
  }

  private LlmConfiguration toConfiguration(Row row) {
    JsonNode value = row == null ? defaultLlm() : row.value();
    return new LlmConfiguration(
        value.path("enabled").asBoolean(false),
        value.path("provider").asText("openai-compatible"),
        value.path("baseUrl").asText("https://generativelanguage.googleapis.com/v1beta/openai"),
        value.path("model").asText("gemini-3.5-flash"),
        value.path("temperature").asDouble(0.2),
        value.path("maxTokens").asInt(1200),
        value.path("timeoutMs").asInt(60000),
        row == null ? "" : secrets.decrypt(row.secret()));
  }

  private JsonNode defaultLlm() {
    ObjectNode value = mapper.createObjectNode();
    value.put("enabled", false);
    value.put("provider", "openai-compatible");
    value.put("baseUrl", "https://generativelanguage.googleapis.com/v1beta/openai");
    value.put("model", "gemini-3.5-flash");
    value.put("temperature", 0.2);
    value.put("maxTokens", 1200);
    value.put("timeoutMs", 60000);
    return value;
  }

  private record Row(JsonNode value, byte[] secret) {
  }
}
