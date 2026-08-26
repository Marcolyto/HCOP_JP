package ar.com.hexium.hcop.media.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.platform.web.ApiException;
import ar.com.hexium.hcop.platform.HcopProperties;
import ar.com.hexium.hcop.media.application.port.in.StudyTemplateUseCase;
import ar.com.hexium.hcop.media.application.port.in.StudyTemplateUseCase.CreateStudyTemplateCommand;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import io.swagger.v3.oas.annotations.Parameter;
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
      @Parameter(description = "Alcance del listado: all, bundled o custom")
      @RequestParam(defaultValue = "all") String scope,
      @Parameter(description = "1 para incluir elementos inactivos, 0 (default) para omitirlos")
      @RequestParam(defaultValue = "0") int includeInactive,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.studies.view");
    return json.view(templates.list(scope, includeInactive == 1));
  }

  @PostMapping("/api/study-templates")
  ResponseEntity<Map<String, Object>> create(
      @Parameter(description = "Título de la plantilla")
      @RequestParam String title,
      @Parameter(description = "Categoría anatómica de la plantilla")
      @RequestParam String category,
      @Parameter(description = "Etiquetas separadas por coma")
      @RequestParam(defaultValue = "") String tags,
      @Parameter(description = "Autor de la plantilla")
      @RequestParam(defaultValue = "") String author,
      @Parameter(description = "Atribución/crédito de la fuente original")
      @RequestParam(defaultValue = "") String attribution,
      @Parameter(description = "Licencia de uso de la imagen")
      @RequestParam(defaultValue = "") String license,
      @Parameter(description = "Descripción de la plantilla")
      @RequestParam(defaultValue = "") String description,
      @Parameter(description = "URL de origen de la imagen")
      @RequestParam(defaultValue = "") String sourceUrl,
      @Parameter(description = "URL con el texto de la licencia")
      @RequestParam(defaultValue = "") String licenseUrl,
      @Parameter(description = "1 confirma que se tienen los derechos de uso de la imagen")
      @RequestParam(defaultValue = "0") int rightsConfirmed,
      @Parameter(description = "Nombre de archivo")
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
