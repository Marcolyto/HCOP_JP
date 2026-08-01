package ar.com.hexium.hcop.guide.infrastructure.web;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.guide.application.port.in.GuideCatalogUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GuideCatalogController {
  private final GuideCatalogUseCase guides;
  private final GuideJsonMapper json;
  private final AuthContext auth;

  public GuideCatalogController(
      GuideCatalogUseCase guides,
      GuideJsonMapper json,
      AuthContext auth) {
    this.guides = guides;
    this.json = json;
    this.auth = auth;
  }

  @GetMapping("/api/guides")
  Map<String, Object> list(
      @RequestParam(defaultValue = "0") int includeInactive,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.tools.view");
    var items = guides.list(includeInactive == 1).stream().map(json::view).toList();
    return Map.of("ok", true, "guides", items, "count", items.size());
  }

  @GetMapping("/api/guides/file")
  ResponseEntity<InputStreamResource> file(
      @RequestParam String name,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.tools.view");
    var guide = guides.open(name);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .contentLength(guide.size())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(guide.name()).build().toString())
        .body(new InputStreamResource(guide.content()));
  }

  @PutMapping(value = "/api/guides/import", consumes = MediaType.APPLICATION_PDF_VALUE)
  Map<String, Object> upload(
      @RequestParam String name,
      HttpServletRequest request) throws IOException {
    auth.requirePermission(request, "section.configuration.manage");
    var result = guides.upload(name, request.getInputStream(), request.getContentLengthLong());
    return Map.of(
        "ok", true,
        "name", result.name(),
        "size", result.size(),
        "replaced", result.replaced());
  }
}
