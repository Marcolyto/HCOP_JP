package ar.com.hexium.hcop.clinicalhistory.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistoryTemplateUseCase;
import ar.com.hexium.hcop.clinicalhistory.application.port.out.ClinicalHistoryTemplatePort;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class ClinicalHistoryTemplateApplicationServiceTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicBoolean called = new AtomicBoolean(false);

  @Test
  void delegatesToTemplatePort() {
    ClinicalHistoryTemplateUseCase useCase = new ClinicalHistoryTemplateApplicationService(new ClinicalHistoryTemplatePort() {
      @Override
      public ObjectNode load() {
        called.set(true);
        ObjectNode template = mapper.createObjectNode();
        template.put("meta", "blank");
        return template;
      }
    });

    var template = useCase.blankTemplate();

    assertThat(called).isTrue();
    assertThat(template.path("meta").asText()).isEqualTo("blank");
  }
}
