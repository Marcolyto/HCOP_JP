package ar.com.hexium.hcop.media.infrastructure.configuration;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.media.application.port.in.ClinicalFileUseCase;
import ar.com.hexium.hcop.media.application.port.in.StudyTemplateUseCase;
import ar.com.hexium.hcop.media.application.port.out.StudyTemplateManifestStore;
import ar.com.hexium.hcop.media.application.service.StudyTemplateApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica los límites transaccionales sin contaminar la capa de aplicación con Spring.
 */
@Service
public class TransactionalStudyTemplateManagement implements StudyTemplateUseCase {
  private final StudyTemplateApplicationService delegate;

  public TransactionalStudyTemplateManagement(
      StudyTemplateManifestStore manifest, ConfigurationManagementUseCase configurations, ClinicalFileUseCase files) {
    this.delegate = new StudyTemplateApplicationService(manifest, configurations, files);
  }

  @Override
  @Transactional(readOnly = true)
  public StudyTemplateCatalog list(String scope, boolean includeInactive) {
    return delegate.list(scope, includeInactive);
  }

  @Override
  @Transactional
  public CreatedStudyTemplate create(CreateStudyTemplateCommand command) {
    return delegate.create(command);
  }
}
