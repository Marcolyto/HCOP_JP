package ar.com.hexium.hcop.treatment;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.media.ClinicalFileService;
import ar.com.hexium.hcop.media.ClinicalFileRepository.StoredFile;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TreatmentDocumentController {
  private final TreatmentDocumentService documents;
  private final ClinicalFileService files;
  private final AuthContext auth;

  public TreatmentDocumentController(
      TreatmentDocumentService documents,
      ClinicalFileService files,
      AuthContext auth) {
    this.documents = documents;
    this.files = files;
    this.auth = auth;
  }

  @GetMapping("/api/clinical/treatments/{treatmentId}/consent")
  ResponseEntity<Resource> consent(
      @PathVariable String treatmentId,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.prescriptions.view");
    return stored(documents.stored(treatmentId, "consent"));
  }

  @GetMapping(
      value = "/api/clinical/patients/{patientId}/treatments/{treatmentId}/documents/treatment-sheet",
      produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> treatmentSheet(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      @RequestParam int cycle,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.prescriptions.view");
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .cacheControl(CacheControl.noStore())
        .body(documents.treatmentSheet(patientId, treatmentId, cycle));
  }

  @GetMapping("/api/clinical/patients/{patientId}/treatments/{treatmentId}/documents/prescription")
  ResponseEntity<Resource> prescription(
      @PathVariable long patientId,
      @PathVariable String treatmentId,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.prescriptions.view");
    return stored(documents.stored(patientId, treatmentId, "prescription"));
  }

  private ResponseEntity<Resource> stored(StoredFile file) {
    Path path = files.path(file);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(file.contentType()));
    headers.setContentLength(file.size());
    headers.setContentDisposition(ContentDisposition.inline().filename(file.originalName()).build());
    headers.setCacheControl(CacheControl.noStore());
    headers.set("X-Content-Type-Options", "nosniff");
    return ResponseEntity.ok().headers(headers).body(new FileSystemResource(path));
  }
}
