package ar.com.hexium.hcop.media.application.port.in;

import ar.com.hexium.hcop.media.domain.ClinicalFile;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

public interface ClinicalFileUseCase {

  StudyUpload uploadStudy(UploadStudyCommand command);

  ClinicalFile storeImage(StoreImageCommand command);

  ClinicalFile requireStudy(String name);

  ClinicalFile requireImage(String name);

  /** Usado por {@code treatment.TreatmentDocumentService} (puerto cruzado). */
  Optional<ClinicalFile> findLatestByTreatment(String treatmentId, String kind);

  Path resolvePath(ClinicalFile file);

  void deleteStudy(String name, String deleteToken);

  /** Revierte una imagen ya guardada cuando falla un paso posterior (p. ej. plantillas). */
  void discardImage(ClinicalFile file);

  record UploadStudyCommand(
      long patientId, String studyId, String requestedName, String declaredContentType,
      InputStream content, UserId actorId, String sessionId) {
  }

  record StoreImageCommand(
      String fileName, byte[] bytes, String contentType, String kind, UserId actorId, String sessionId) {
  }

  record StudyUpload(ClinicalFile file, String deleteToken) {
  }
}
