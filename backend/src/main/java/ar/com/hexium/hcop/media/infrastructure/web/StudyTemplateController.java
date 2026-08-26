package ar.com.hexium.hcop.media.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.platform.web.ApiException;
import ar.com.hexium.hcop.platform.HcopProperties;
import ar.com.hexium.hcop.media.application.port.in.StudyTemplateUseCase;
import ar.com.hexium.hcop.media.application.port.in.StudyTemplateUseCase.CreateStudyTemplateCommand;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudyTemplateController {
  private final StudyTemplateUseCase templates;
  private final StudyTemplateJsonMapper json;
  private final AuthContext auth;
  private final long maxImageBytes;

  public StudyTemplateController(
      StudyTemplateUseCase templates, StudyTemplateJsonMapper json, AuthContext auth, HcopProperties properties) {
    this.templates = templates;
    this.json = json;
    this.auth = auth;
    this.maxImageBytes = properties.maxImageBytes();
  }

  @GetMapping("/api/study-templates")
  Map<String, Object> list(
      @RequestParam(defaultValue = "all") String scope,
      @RequestParam(defaultValue = "0") int includeInactive,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.studies.view");
    return json.view(templates.list(scope, includeInactive == 1));
  }

  @PostMapping("/api/study-templates")
  ResponseEntity<Map<String, Object>> create(
      @RequestParam String title,
      @RequestParam String category,
      @RequestParam(defaultValue = "") String tags,
      @RequestParam(defaultValue = "") String author,
      @RequestParam(defaultValue = "") String attribution,
      @RequestParam(defaultValue = "") String license,
      @RequestParam(defaultValue = "") String description,
      @RequestParam(defaultValue = "") String sourceUrl,
      @RequestParam(defaultValue = "") String licenseUrl,
      @RequestParam(defaultValue = "0") int rightsConfirmed,
      @RequestParam(name = "name", defaultValue = "plantilla") String fileName,
      HttpServletRequest request) throws IOException {
    auth.requirePermission(request, "section.configuration.manage");
    long declared = request.getContentLengthLong();
    if (declared == 0 || declared > maxImageBytes) {
      throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "La plantilla está vacía o supera el límite permitido.");
    }
    byte[] bytes = request.getInputStream().readNBytes(Math.toIntExact(maxImageBytes + 1));
    if (bytes.length == 0 || bytes.length > maxImageBytes) {
      throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "La plantilla está vacía o supera el límite permitido.");
    }
    String contentType = request.getContentType() == null ? "image/png"
        : request.getContentType().split(";")[0].trim();
    var actor = auth.require(request);
    var created = templates.create(new CreateStudyTemplateCommand(
        title, category, List.of(tags.split(",")), author, attribution, license, description,
        sourceUrl, licenseUrl, rightsConfirmed == 1, fileName, bytes, contentType,
        UserId.of(actor.userId()), auth.sessionId(request)));
    return ResponseEntity.status(HttpStatus.CREATED).body(json.created(created.item()));
  }
}
