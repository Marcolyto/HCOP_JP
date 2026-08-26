package ar.com.hexium.hcop.media.infrastructure.configuration;

import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.media.application.port.in.ClinicalFileUseCase;
import ar.com.hexium.hcop.media.application.port.out.ClinicalFileBlobStore;
import ar.com.hexium.hcop.media.application.port.out.ClinicalFileStore;
import ar.com.hexium.hcop.media.application.port.out.PatientLookupPort;
import ar.com.hexium.hcop.media.application.service.ClinicalFileApplicationService;
import ar.com.hexium.hcop.media.domain.ClinicalFile;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica los límites transaccionales sin contaminar la capa de aplicación con Spring. Solo
 * envuelve el registro en base de datos — el filesystem no participa de la transacción.
 */
@Service
public class TransactionalClinicalFileManagement implements ClinicalFileUseCase {
  private final ClinicalFileApplicationService delegate;

  public TransactionalClinicalFileManagement(
      ClinicalFileStore store, ClinicalFileBlobStore blobs, PatientLookupPort patients,
      AuthService auth, Clock clock) {
    this.delegate = new ClinicalFileApplicationService(store, blobs, patients, auth, clock);
  }

  @Override
  @Transactional
  public StudyUpload uploadStudy(UploadStudyCommand command) {
    return delegate.uploadStudy(command);
  }

  @Override
  @Transactional
  public ClinicalFile storeImage(StoreImageCommand command) {
    return delegate.storeImage(command);
  }

  @Override
  @Transactional(readOnly = true)
  public ClinicalFile requireStudy(String name) {
    return delegate.requireStudy(name);
  }

  @Override
  @Transactional(readOnly = true)
  public ClinicalFile requireImage(String name) {
    return delegate.requireImage(name);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ClinicalFile> findLatestByTreatment(String treatmentId, String kind) {
    return delegate.findLatestByTreatment(treatmentId, kind);
  }

  @Override
  public Path resolvePath(ClinicalFile file) {
    return delegate.resolvePath(file);
  }

  @Override
  @Transactional
  public void deleteStudy(String name, String deleteToken) {
    delegate.deleteStudy(name, deleteToken);
  }

  @Override
  @Transactional
  public void discardImage(ClinicalFile file) {
    delegate.discardImage(file);
  }
}
