package ar.com.hexium.hcop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.integration.SystemSettingsRepository.Setting;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class SystemConfigServiceTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private SystemSettingsRepository settings;
  private SecretBox secrets;
  private SystemConfigService service;

  @BeforeEach
  void setUp() {
    settings = mock(SystemSettingsRepository.class);
    secrets = mock(SecretBox.class);
    service = new SystemConfigService(settings, secrets, mapper);
  }

  @Test
  void noPermiteHabilitarGeminiSinUnaClaveEfectiva() {
    when(settings.find("llm")).thenReturn(Optional.empty());

    assertApiKeyRequired(() -> service.update(configuration("keep", true, false), 7L));

    verify(settings, never()).upsert(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyBoolean(),
        org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void quitarLaClaveNoUsaElSecretoAnteriorAlProbarElBorrador() {
    byte[] encrypted = new byte[] {1, 2, 3};
    when(settings.find("llm")).thenReturn(Optional.of(storedGemini(encrypted)));
    when(secrets.decrypt(encrypted)).thenReturn("saved-key");

    assertApiKeyRequired(() -> service.draft(configuration("remove", true, false)));
  }

  @Test
  void reemplazarLaClaveSeUsaEnElBorradorSinPersistirla() {
    when(settings.find("llm")).thenReturn(Optional.empty());

    var draft = service.draft(configuration("replace", true, true));

    assertThat(draft.apiKey()).isEqualTo("new-key");
    assertThat(draft.provider()).isEqualTo("gemini");
    verify(settings, never()).upsert(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyBoolean(),
        org.mockito.ArgumentMatchers.anyLong());
  }

  private ObjectNode configuration(String action, boolean enabled, boolean includeKey) {
    ObjectNode llm = mapper.createObjectNode();
    llm.put("enabled", enabled);
    llm.put("provider", "gemini");
    llm.put("baseUrl", "https://generativelanguage.googleapis.com/v1beta/openai");
    llm.put("model", "gemini-3.5-flash");
    llm.put("apiKeyAction", action);
    if (includeKey) llm.put("apiKey", "new-key");
    return mapper.createObjectNode().set("llm", llm);
  }

  private Setting storedGemini(byte[] encrypted) {
    ObjectNode value = mapper.createObjectNode();
    value.put("enabled", true);
    value.put("provider", "gemini");
    value.put("baseUrl", "https://generativelanguage.googleapis.com/v1beta/openai");
    value.put("model", "gemini-3.5-flash");
    value.put("temperature", 0.2);
    value.put("maxTokens", 1200);
    value.put("timeoutMs", 60_000);
    return new Setting("llm", value, encrypted, 1L);
  }

  private void assertApiKeyRequired(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .satisfies(error -> {
          ApiException api = (ApiException) error;
          assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(api.code()).isEqualTo("LLM_API_KEY_REQUIRED");
        });
  }
}
