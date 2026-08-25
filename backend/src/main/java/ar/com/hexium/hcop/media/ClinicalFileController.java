package ar.com.hexium.hcop.media;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.media.ClinicalFileRepository.StoredFile;
import ar.com.hexium.hcop.media.ClinicalFileService.StudyUpload;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class ClinicalFileController {
  private final ClinicalFileService files;
  private final AuthContext auth;

  public ClinicalFileController(ClinicalFileService files, AuthContext auth) {
    this.files = files;
    this.auth = auth;
  }

  @PostMapping("/api/media/studies")
  ResponseEntity<Map<String, Object>> uploadStudy(
      @RequestParam long patientId,
      @RequestParam String studyId,
      @RequestParam(name = "name") String fileName,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.studies.edit");
    StudyUpload upload = files.uploadStudy(
        request, patientId, studyId, fileName, auth.require(request), auth.token(request));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(withOk(files.studyView(upload.file(), upload.deleteToken())));
  }

  @GetMapping("/api/media/studies/{name:.+}")
  ResponseEntity<Resource> study(
      @PathVariable String name,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.studies.view");
    StoredFile file = files.requireByStorageName("studies", name);
    return file(file, files.path(file), false);
  }

  @DeleteMapping("/api/media/studies/{name:.+}")
  Map<String, Object> deleteStudy(
      @PathVariable String name,
      @RequestHeader(name = "X-Study-Delete-Token", defaultValue = "") String deleteToken,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.studies.edit");
    files.deleteStudy(name, deleteToken);
    return Map.of("ok", true, "deleted", true, "name", name);
  }

  @PostMapping("/api/media/images")
  ResponseEntity<Map<String, Object>> uploadImage(
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.studies.edit");
    if (body.hasNonNull("sourceUrl") && !body.path("sourceUrl").asText("").isBlank()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "La importación remota debe realizarse desde una fuente configurada y verificada.");
    }
    String dataUrl = body.path("dataUrl").asText("");
    int comma = dataUrl.indexOf(',');
    if (!dataUrl.startsWith("data:image/") || comma < 0 || !dataUrl.substring(0, comma).contains(";base64")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "La imagen no tiene un formato válido.");
    }
    String contentType = dataUrl.substring("data:".length(), dataUrl.indexOf(';')).toLowerCase();
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
    } catch (IllegalArgumentException invalid) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "La imagen no tiene un formato válido.");
    }
    SessionPrincipal actor = auth.require(request);
    StoredFile stored = files.storeImage(
        body.path("fileName").asText("imagen"),
        bytes,
        contentType,
        body.path("kind").asText("original"),
        actor,
        auth.token(request));
    return ResponseEntity.status(HttpStatus.CREATED).body(withOk(files.imageView(stored)));
  }

  @GetMapping("/api/media/images/{name:.+}")
  ResponseEntity<Resource> image(
      @PathVariable String name,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.studies.view");
    StoredFile file = files.requireByStorageName("images", name);
    return file(file, files.path(file), true);
  }

  private ResponseEntity<Resource> file(StoredFile file, Path path, boolean immutable) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(file.contentType()));
    headers.setContentLength(file.size());
    headers.setContentDisposition(ContentDisposition.inline().filename(file.originalName()).build());
    headers.setCacheControl(immutable
        ? CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePrivate().immutable()
        : CacheControl.noStore());
    headers.set("X-Content-Type-Options", "nosniff");
    headers.set("Cross-Origin-Resource-Policy", "same-origin");
    return ResponseEntity.ok().headers(headers).body(new FileSystemResource(path));
  }

  private Map<String, Object> withOk(Map<String, Object> value) {
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("ok", true);
    result.putAll(value);
    return result;
  }
}
