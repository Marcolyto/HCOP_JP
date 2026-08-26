package ar.com.hexium.hcop.integration.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.platform.web.ApiException;
import ar.com.hexium.hcop.integration.application.port.in.AgentChatUseCase;
import ar.com.hexium.hcop.integration.application.port.in.AgentChatUseCase.AgentChatCommand;
import ar.com.hexium.hcop.integration.application.port.in.ClinicalSummaryUseCase;
import ar.com.hexium.hcop.integration.application.port.in.ClinicalTimelineExtractionUseCase;
import ar.com.hexium.hcop.integration.application.port.in.LlmConnectionTestUseCase;
import ar.com.hexium.hcop.integration.application.port.in.LlmStatusUseCase;
import ar.com.hexium.hcop.integration.application.port.in.LlmStatusUseCase.LlmStatus;
import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase;
import ar.com.hexium.hcop.integration.application.port.in.SystemicFormFillUseCase;
import ar.com.hexium.hcop.integration.domain.AgentAnswer;
import ar.com.hexium.hcop.integration.infrastructure.web.LlmController.AgentChatRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class LlmControllerTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final SystemConfigurationUseCase configuration = mock(SystemConfigurationUseCase.class);
  private final LlmStatusUseCase status = mock(LlmStatusUseCase.class);
  private final LlmConnectionTestUseCase connectionTest = mock(LlmConnectionTestUseCase.class);
  private final ClinicalTimelineExtractionUseCase timelineExtraction = mock(ClinicalTimelineExtractionUseCase.class);
  private final ClinicalSummaryUseCase summary = mock(ClinicalSummaryUseCase.class);
  private final AgentChatUseCase agentChat = mock(AgentChatUseCase.class);
  private final SystemicFormFillUseCase formFill = mock(SystemicFormFillUseCase.class);
  private final AuthContext auth = mock(AuthContext.class);
  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private LlmController controller;

  @BeforeEach
  void setUp() {
    controller = new LlmController(
        configuration, status, connectionTest, timelineExtraction, summary, agentChat, formFill,
        new IntegrationJsonMapper(mapper), auth, mapper);
  }

  @Test
  void exigePermisoDeAgenteAntesDeConsultarElEstado() {
    ApiException forbidden = new ApiException(HttpStatus.FORBIDDEN, "Sin permiso");
    doThrow(forbidden).when(auth).requirePermission(request, "section.agent.view");

    assertThatThrownBy(() -> controller.status(request)).isSameAs(forbidden);

    verifyNoInteractions(status);
  }

  @Test
  void devuelveElEstadoTipadoSiempreConOkTrue() {
    when(status.status()).thenReturn(new LlmStatus(true, "modelo-clinico", "openai-compatible", true));

    var response = controller.status(request);

    assertThat(response.ok()).isTrue();
    assertThat(response.enabled()).isTrue();
    assertThat(response.model()).isEqualTo("modelo-clinico");
    assertThat(response.provider()).isEqualTo("openai-compatible");
    assertThat(response.configured()).isTrue();
    verify(auth).requirePermission(request, "section.agent.view");
  }

  @Test
  void exigePermisoDeAgenteAntesDeEnviarLaConsulta() {
    ApiException forbidden = new ApiException(HttpStatus.FORBIDDEN, "Sin permiso");
    doThrow(forbidden).when(auth).requirePermission(request, "section.agent.view");

    assertThatThrownBy(() -> controller.agent(chat("consulta"), request)).isSameAs(forbidden);

    verifyNoInteractions(agentChat);
  }

  @Test
  void mapeaElComandoYLaRespuestaDelAgente() {
    when(agentChat.chat(any(AgentChatCommand.class))).thenReturn(
        new AgentAnswer("Hallazgo documentado", "modelo-proveedor", java.util.List.of(), java.util.List.of(), java.util.List.of()));

    var response = controller.agent(chat("consulta"), request);

    assertThat(response.ok()).isTrue();
    assertThat(response.answer()).isEqualTo("Hallazgo documentado");
    assertThat(response.model()).isEqualTo("modelo-proveedor");
    assertThat(response.artifacts()).isEmpty();
    verify(auth).requirePermission(request, "section.agent.view");
  }

  @Test
  void aceptaCamposDesconocidosSinRomperElContratoCompatible() throws Exception {
    AgentChatRequest mapped = mapper.readValue("""
        {
          "message": "consulta",
          "timelineEvents": [{"date": "2026-08-01"}],
          "consultAgents": false,
          "legacyField": {"stillAccepted": true}
        }
        """, AgentChatRequest.class);

    assertThat(mapped.message()).isEqualTo("consulta");
    assertThat(mapped.timelineEvents()).isNotNull();
    assertThat(mapped.consultAgents()).isFalse();
  }

  @Test
  void rechazaResumenSinEventos() throws Exception {
    var body = mapper.readTree("{\"period\":\"2026\",\"events\":[]}");

    assertThatThrownBy(() -> controller.summarize(body, request))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> {
          ApiException api = (ApiException) error;
          assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(api.getMessage()).isEqualTo("No se recibieron eventos.");
        });
  }

  private AgentChatRequest chat(String message) {
    return new AgentChatRequest(message, "", java.util.List.of(), null, false);
  }
}
