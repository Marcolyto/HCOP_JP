package ar.com.hexium.hcop.clinicalhistory.application.service;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistoryTemplateUseCase;
import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalHistoryTemplatePort;
import tools.jackson.databind.JsonNode;

public final class ClinicalHistoryTemplateApplicationService implements ClinicalHistoryTemplateUseCase {
  private final ClinicalHistoryTemplatePort templates;

  public ClinicalHistoryTemplateApplicationService(ClinicalHistoryTemplatePort templates) {
    this.templates = templates;
  }

  @Override
  public JsonNode blankTemplate() {
    return templates.load();
  }
}
