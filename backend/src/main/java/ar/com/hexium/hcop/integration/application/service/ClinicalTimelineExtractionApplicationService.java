package ar.com.hexium.hcop.integration.application.service;

import ar.com.hexium.hcop.integration.application.port.in.ClinicalTimelineExtractionUseCase;
import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase;
import ar.com.hexium.hcop.integration.application.port.out.LlmPort;
import ar.com.hexium.hcop.integration.domain.ChatMessage;
import java.util.List;
import java.util.Map;

public final class ClinicalTimelineExtractionApplicationService implements ClinicalTimelineExtractionUseCase {
  private final SystemConfigurationUseCase configuration;
  private final LlmPort llm;

  public ClinicalTimelineExtractionApplicationService(SystemConfigurationUseCase configuration, LlmPort llm) {
    this.configuration = configuration;
    this.llm = llm;
  }

  @Override
  public TimelineExtraction extractTimeline(String clinicalText) {
    String text = ClinicalTextLimits.limit(clinicalText);
    if (text.isBlank()) {
      throw new IntegrationFailure(IntegrationFailure.Type.INVALID, "No hay historia para analizar.");
    }
    String prompt = """
        Extraé una cronología clínica oncológica exhaustiva. Respondé SOLO JSON válido con:
        {"events":[{"date":"YYYY-MM-DD","datePrecision":"day|month|year","category":"diagnosis|evolution|study|pathology|prescription|surgery|chemotherapy|radiotherapy|immunotherapy|hormone|targeted|research|indication","title":"","body":"","highlighted":false,"phase":"","clinicalStatus":"","sourceQuote":""}],"warnings":[]}.
        No inventes datos, preservá todas las fechas y separá eventos distintos.

        HISTORIA:
        """ + text;
    var response = llm.complete(
        configuration.currentConfiguration(),
        List.of(new ChatMessage("system", "Sos un extractor clínico preciso y auditable."),
            new ChatMessage("user", prompt)),
        true);
    Object parsed = llm.parseJson(response.content());
    return new TimelineExtraction(list(parsed, "events"), list(parsed, "warnings"), response.model());
  }

  @SuppressWarnings("unchecked")
  private List<Object> list(Object parsed, String key) {
    if (parsed instanceof Map<?, ?> map && map.get(key) instanceof List<?> list) {
      return (List<Object>) list;
    }
    return List.of();
  }
}
