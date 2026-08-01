package ar.com.hexium.hcop.clinicalhistory.application.port.in;

import tools.jackson.databind.JsonNode;

public interface ClinicalHistoryTemplateUseCase {
  JsonNode blankTemplate();
}
