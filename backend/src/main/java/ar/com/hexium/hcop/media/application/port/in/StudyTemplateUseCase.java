package ar.com.hexium.hcop.media.application.port.in;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.ConfigurationView;
import ar.com.hexium.hcop.media.domain.ClinicalFile;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.util.List;

public interface StudyTemplateUseCase {

  StudyTemplateCatalog list(String scope, boolean includeInactive);

  CreatedStudyTemplate create(CreateStudyTemplateCommand command);

  /** {@code bundledTemplates}: árboles opacos del manifiesto (ya con origin/active/available). */
  record StudyTemplateCatalog(List<Object> bundledTemplates, List<ConfigurationView> customTemplates) {
  }

  record CreateStudyTemplateCommand(
      String title, String category, List<String> tags, String author, String attribution,
      String license, String description, String sourceUrl, String licenseUrl,
      boolean rightsConfirmed, String fileName, byte[] imageBytes, String imageContentType,
      UserId actorId, String sessionId) {
  }

  record CreatedStudyTemplate(ConfigurationView item, ClinicalFile image) {
  }
}
