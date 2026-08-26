package ar.com.hexium.hcop.integration.application.port.in;

public interface ClinicalSummaryUseCase {

  /** {@code eventsJson}: hasta 250 eventos, ya serializados a JSON por la capa web (Jackson). */
  ClinicalSummary summarize(String period, String eventsJson);

  record ClinicalSummary(String model, String summary) {
  }
}
