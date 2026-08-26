package ar.com.hexium.hcop.integration.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.integration.application.port.in.AgentChatUseCase.AgentChatCommand;
import ar.com.hexium.hcop.integration.application.port.in.AgentChatUseCase.HistoryEntry;
import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase;
import ar.com.hexium.hcop.integration.application.port.out.LlmPort;
import ar.com.hexium.hcop.integration.domain.AgentAnswer;
import ar.com.hexium.hcop.integration.domain.ChatHighlight;
import ar.com.hexium.hcop.integration.domain.ChatMessage;
import ar.com.hexium.hcop.integration.domain.LlmConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentChatApplicationServiceTest {
  private final SystemConfigurationUseCase configuration = mock(SystemConfigurationUseCase.class);
  private final LlmPort llm = mock(LlmPort.class);
  private AgentChatApplicationService service;
  private final LlmConfiguration config = new LlmConfiguration(
      true, "openai-compatible", "https://llm.example.test/v1", "modelo-clinico", 0.2, 1200, 60_000, "secret");

  @BeforeEach
  void setUp() {
    service = new AgentChatApplicationService(configuration, llm);
    when(configuration.currentConfiguration()).thenReturn(config);
  }

  @Test
  void rechazaConsultaVacia() {
    assertThatThrownBy(() -> service.chat(new AgentChatCommand(" \n\t ", "", List.of())))
        .isInstanceOf(IntegrationFailure.class)
        .satisfies(error -> {
          IntegrationFailure failure = (IntegrationFailure) error;
          assertThat(failure.type()).isEqualTo(IntegrationFailure.Type.INVALID);
          assertThat(failure.getMessage()).isEqualTo("Escriba una consulta.");
        });
  }

  @Test
  void rechazaUnaConsultaQueSuperaElLimiteSeguro() {
    String oversized = "x".repeat(AgentChatApplicationService.MAX_AGENT_MESSAGE_CHARS + 1);

    assertThatThrownBy(() -> service.chat(new AgentChatCommand(oversized, "", List.of())))
        .isInstanceOf(IntegrationFailure.class)
        .satisfies(error -> assertThat(error.getMessage()).contains("8000"));
  }

  @Test
  void limitaElHistorialALosMensajesRecientesYAcotaCadaContenido() {
    List<HistoryEntry> history = new ArrayList<>();
    for (int index = 0; index < AgentChatApplicationService.MAX_AGENT_HISTORY_MESSAGES + 3; index++) {
      String content = index == 3
          ? "h-3-" + "x".repeat(AgentChatApplicationService.MAX_AGENT_HISTORY_MESSAGE_CHARS)
          : "h-" + index;
      history.add(new HistoryEntry(index % 2 == 0 ? "user" : "assistant", content));
    }
    stubCompletion(emptyAnswer());

    service.chat(new AgentChatCommand("consulta final", "contexto", history));

    List<ChatMessage> sent = capturedMessages();
    assertThat(sent).hasSize(3 + AgentChatApplicationService.MAX_AGENT_HISTORY_MESSAGES + 1);
    assertThat(sent.get(3).content())
        .startsWith("h-3-")
        .hasSize(AgentChatApplicationService.MAX_AGENT_HISTORY_MESSAGE_CHARS);
    assertThat(sent.subList(3, sent.size() - 1))
        .extracting(ChatMessage::content)
        .hasSize(AgentChatApplicationService.MAX_AGENT_HISTORY_MESSAGES)
        .endsWith("h-14");
    assertThat(sent.get(sent.size() - 1)).isEqualTo(new ChatMessage("user", "consulta final"));
  }

  @Test
  void noDuplicaLaConsultaActualCuandoYaEsElUltimoMensajeDelHistorial() {
    stubCompletion(emptyAnswer());
    List<HistoryEntry> history = List.of(
        new HistoryEntry("user", "consulta anterior"),
        new HistoryEntry("assistant", "respuesta anterior"),
        new HistoryEntry("user", "  consulta actual  "));

    service.chat(new AgentChatCommand("consulta actual", "contexto", history));

    List<ChatMessage> sent = capturedMessages();
    assertThat(sent)
        .filteredOn(message -> "user".equals(message.role()) && "consulta actual".equals(message.content()))
        .hasSize(1);
    assertThat(sent.get(sent.size() - 1)).isEqualTo(new ChatMessage("user", "consulta actual"));
  }

  @Test
  void solicitaRespuestaEstructuradaProhibiendoTablasMarkdown() {
    stubCompletion(emptyAnswer());

    service.chat(new AgentChatCommand("Armame una tabla de PSA", "", List.of()));

    List<ChatMessage> sent = capturedMessages();
    assertThat(sent)
        .anyMatch(message -> message.content().contains("No uses tablas Markdown")
            && message.content().contains("20 puntos")
            && message.content().contains("No clasifiques valores"));
  }

  @Test
  void conservaSoloHighlightsLiteralesDelContextoNormalizado() {
    stubCompletion(new AgentAnswer(
        "Valores documentados.", "modelo-proveedor", List.of(), List.of(),
        List.of(
            new ChatHighlight(List.of("PSA TOTAL 4,2 ng/mL", "PSA total 99 ng/mL"), "study"),
            new ChatHighlight(List.of("metástasis ósea"), "pathology"))));

    AgentAnswer response = service.chat(new AgentChatCommand(
        "consulta", "Laboratorio: PSA total 4,2 ng/mL.", List.of()));

    assertThat(response.highlights()).hasSize(1);
    assertThat(response.highlights().get(0).terms()).containsExactly("PSA TOTAL 4,2 ng/mL");
    assertThat(response.highlights().get(0).color()).isEqualTo("study");
  }

  @Test
  void acotaCantidadDeHighlightsYTerminosYNormalizaElColorInvalido() {
    List<ChatHighlight> highlights = new ArrayList<>();
    for (int index = 0; index < 25; index++) {
      List<String> terms = new ArrayList<>();
      for (int term = 0; term < 25; term++) terms.add("término-" + index + "-" + term);
      highlights.add(new ChatHighlight(terms, "chemotherapy"));
    }
    String clinicalText = highlights.stream()
        .flatMap(h -> h.terms().stream()).reduce("", (a, b) -> a + " " + b);
    stubCompletion(new AgentAnswer("Respuesta", "modelo", List.of(), List.of(), highlights));

    AgentAnswer response = service.chat(new AgentChatCommand("consulta", clinicalText, List.of()));

    assertThat(response.highlights()).hasSize(AgentChatApplicationService.MAX_AGENT_HIGHLIGHTS);
    assertThat(response.highlights().get(0).terms()).hasSize(AgentChatApplicationService.MAX_AGENT_TERMS);
    assertThat(response.highlights().get(0).color()).isEqualTo("chemotherapy");
  }

  @Test
  void propagaSinTraducirLosErroresExistentesDelPuertoLlm() {
    IntegrationFailure upstream = new IntegrationFailure(
        IntegrationFailure.Type.UNAVAILABLE, "El servicio LLM está desactivado.", "LLM_DISABLED");
    when(llm.completeAgentChat(eq(config), anyList())).thenThrow(upstream);

    assertThatThrownBy(() -> service.chat(new AgentChatCommand("consulta", "", List.of())))
        .isSameAs(upstream);
  }

  private AgentAnswer emptyAnswer() {
    return new AgentAnswer("Respuesta", "modelo-respuesta", List.of(), List.of(), List.of());
  }

  private void stubCompletion(AgentAnswer answer) {
    when(llm.completeAgentChat(eq(config), anyList())).thenReturn(answer);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private List<ChatMessage> capturedMessages() {
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(llm).completeAgentChat(eq(config), captor.capture());
    return (List<ChatMessage>) captor.getValue();
  }
}
