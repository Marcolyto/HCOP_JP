package ar.com.hexium.hcop.integration.application.service;

import ar.com.hexium.hcop.integration.application.port.in.ClinicalSummaryUseCase;
import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase;
import ar.com.hexium.hcop.integration.application.port.out.LlmPort;
import ar.com.hexium.hcop.integration.domain.ChatMessage;
import java.util.List;

public final class ClinicalSummaryApplicationService implements ClinicalSummaryUseCase {
  private final SystemConfigurationUseCase configuration;
  private final LlmPort llm;

  public ClinicalSummaryApplicationService(SystemConfigurationUseCase configuration, LlmPort llm) {
    this.configuration = configuration;
    this.llm = llm;
  }

  @Override
  public ClinicalSummary summarize(String period, String eventsJson) {
    String prompt = """
        Resumí solamente los datos aportados, sin inventar. Omití nombres de profesionales.
        Priorizá diagnóstico, estadio, tratamientos, respuesta, progresión, toxicidad,
        internaciones y conducta. Respondé en español con puntos breves pero completos.

        PERÍODO:
        """ + ClinicalTextLimits.limit(period) + "\nEVENTOS:\n" + ClinicalTextLimits.limit(eventsJson);
    var response = llm.complete(
        configuration.currentConfiguration(),
        List.of(
            new ChatMessage("system", "Sos un asistente de resumen clínico oncológico preciso y auditable."),
            new ChatMessage("user", prompt)),
        true);
    return new ClinicalSummary(response.model(), response.content());
  }
}
