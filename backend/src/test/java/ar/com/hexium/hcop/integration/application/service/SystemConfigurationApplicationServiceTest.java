package ar.com.hexium.hcop.integration.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase.LlmConfigurationCommand;
import ar.com.hexium.hcop.integration.application.port.out.LlmConfigurationStore;
import ar.com.hexium.hcop.integration.domain.LlmConfiguration;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import org.junit.jupiter.api.Test;

class SystemConfigurationApplicationServiceTest {
  private final LlmConfigurationStore store = mock(LlmConfigurationStore.class);
  private final SystemConfigurationApplicationService service = new SystemConfigurationApplicationService(store);
  private static final LlmConfiguration DEFAULT = new LlmConfiguration(
      false, "openai-compatible", "https://generativelanguage.googleapis.com/v1beta/openai",
      "gemini-3.5-flash", 0.2, 1200, 60000, "");

  @Test
  void noPermiteHabilitarGeminiSinUnaClaveEfectiva() {
    when(store.find()).thenReturn(DEFAULT);
    LlmConfigurationCommand command = command("keep", true, false);

    assertApiKeyRequired(() -> service.update(command, UserId.of(7)));

    verify(store, never()).upsert(any(), anyBoolean(), anyLong());
  }

  @Test
  void quitarLaClaveNoUsaElSecretoAnteriorAlProbarElBorrador() {
    when(store.find()).thenReturn(new LlmConfiguration(
        true, "gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
        "gemini-3.5-flash", 0.2, 1200, 60000, "saved-key"));

    assertApiKeyRequired(() -> service.draftConfiguration(command("remove", true, false)));
  }

  @Test
  void reemplazarLaClaveSeUsaEnElBorradorSinPersistirla() {
    when(store.find()).thenReturn(DEFAULT);

    LlmConfiguration draft = service.draftConfiguration(command("replace", true, true));

    assertThat(draft.apiKey()).isEqualTo("new-key");
    assertThat(draft.provider()).isEqualTo("gemini");
    verify(store, never()).upsert(any(), anyBoolean(), anyLong());
  }

  private LlmConfigurationCommand command(String action, boolean enabled, boolean includeKey) {
    return new LlmConfigurationCommand(
        enabled, "gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
        "gemini-3.5-flash", null, null, null, action, includeKey ? "new-key" : "", includeKey);
  }

  private void assertApiKeyRequired(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(IntegrationFailure.class)
        .satisfies(error -> {
          IntegrationFailure failure = (IntegrationFailure) error;
          assertThat(failure.type()).isEqualTo(IntegrationFailure.Type.INVALID);
          assertThat(failure.code()).isEqualTo("LLM_API_KEY_REQUIRED");
        });
  }
}
