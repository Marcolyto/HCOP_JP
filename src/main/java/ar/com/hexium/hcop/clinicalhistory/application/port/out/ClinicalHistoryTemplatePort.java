package ar.com.hexium.hcop.clinicalhistory.application.port.out;

import tools.jackson.databind.JsonNode;

public interface ClinicalHistoryTemplatePort {
  JsonNode load();
}
