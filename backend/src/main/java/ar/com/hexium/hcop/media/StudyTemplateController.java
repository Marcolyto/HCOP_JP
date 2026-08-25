package ar.com.hexium.hcop.media;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.configuration.ConfigurationService;
import ar.com.hexium.hcop.media.ClinicalFileRepository.StoredFile;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@RestController
public class StudyTemplateController {
  private final ConfigurationService configurations;
  private final ClinicalFileService files;
  private final AuthContext auth;
  private final ObjectMapper mapper;
  private final long maxImageBytes;

  public StudyTemplateController(
      ConfigurationService configurations,
      ClinicalFileService files,
      AuthContext auth,
      ObjectMapper mapper,
      HcopProperties properties) {
    this.configurations = configurations;
    this.files = files;
    this.auth = auth;
    this.mapper = mapper;
    this.maxImageBytes = properties.maxImageBytes();
  }

  @GetMapping("/api/study-templates")
  Map<String, Object> list(
      @RequestParam(defaultValue = "all") String scope,
      @RequestParam(defaultValue = "0") int includeInactive,
      HttpServletRequest request) {
    auth.requirePermission(request, "section.studies.view");
    JsonNode manifest = manifest();
    List<Object> templates = new ArrayList<>();
    if (!"custom".equals(scope)) {
      for (JsonNode template : manifest.path("templates")) {
        Map<String, Object> row = mapper.convertValue(template, Map.class);
        row.put("origin", "bundled");
        row.put("active", true);
        row.put("available", true);
        templates.add(row);
      }
    }
    List<Map<String, Object>> custom = configurations.list("study-template", includeInactive == 1);
    for (Map<String, Object> item : custom) templates.add(customTemplate(item));
    Map<String, Integer> categoryCounts = new LinkedHashMap<>();
    for (Object raw : templates) {
      Map<?, ?> item = (Map<?, ?>) raw;
      Object categoryValue = item.containsKey("category") ? item.get("category") : "otros";
      String category = String.valueOf(categoryValue);
      categoryCounts.merge(category, 1, Integer::sum);
    }
    List<Map<String, Object>> categories = categoryCounts.entrySet().stream()
        .map(entry -> Map.<String, Object>of(
            "id", entry.getKey(),
            "title", entry.getKey().replace('-', ' '),
            "count", entry.getValue()))
        .toList();
    return Map.of(
        "ok", true,
        "version", 2,
        "bundledCount", manifest.path("templates").size(),
        "customCount", custom.size(),
        "total", templates.size(),
        "categories", categories,
        "templates", templates,
        "customAvailable", true);
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
    if (rightsConfirmed != 1) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Debe confirmar los derechos de uso.");
    }
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
    StoredFile image = files.storeImage(
        fileName, bytes, contentType, "original", auth.require(request), auth.token(request));
    Map<String, Object> imageView = files.imageView(image);
    ObjectNode body = mapper.createObjectNode();
    body.put("name", title.trim());
    body.put("description", description.trim());
    body.put("active", true);
    ObjectNode definition = body.putObject("definition");
    definition.put("origin", "custom");
    definition.put("category", category.trim());
    definition.put("author", author.trim());
    definition.put("attribution", attribution.trim());
    definition.put("license", license.trim());
    definition.put("sourceUrl", sourceUrl.trim());
    definition.put("licenseUrl", licenseUrl.trim());
    definition.put("rightsConfirmed", true);
    definition.put("fileName", String.valueOf(imageView.get("name")));
    definition.put("fileUrl", String.valueOf(imageView.get("url")));
    definition.put("thumbnailUrl", String.valueOf(imageView.get("url")));
    definition.put("mime", image.contentType());
    definition.put("bytes", image.size());
    definition.put("sha256", image.sha256());
    var tagArray = definition.putArray("tags");
    for (String tag : tags.split(",")) {
      String normalized = tag.trim();
      if (!normalized.isBlank() && tagArray.size() < 30) tagArray.add(normalized);
    }
    Map<String, Object> item;
    try {
      item = configurations.create("study-template", body, auth.require(request).userId());
    } catch (RuntimeException failure) {
      try {
        files.discardImage(image);
      } catch (RuntimeException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
        "ok", true,
        "item", item,
        "template", customTemplate(item)));
  }

  private JsonNode manifest() {
    try {
      ClassPathResource resource = new ClassPathResource("static/assets/study-templates/manifest.json");
      return mapper.readTree(resource.getInputStream());
    } catch (IOException error) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo abrir la biblioteca anatómica.");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> customTemplate(Map<String, Object> item) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", "custom-" + item.get("id"));
    row.put("configurationId", item.get("id"));
    row.put("origin", "custom");
    row.put("title", item.get("name"));
    row.put("description", item.get("description"));
    row.put("active", item.get("active"));
    row.put("available", true);
    Object definitionValue = item.get("definition");
    Map<String, Object> definition = definitionValue instanceof JsonNode node
        ? mapper.convertValue(node, Map.class)
        : definitionValue instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    row.putAll(definition);
    row.put("file", definition.getOrDefault("fileUrl", ""));
    row.put("thumbnail", definition.getOrDefault("thumbnailUrl", definition.getOrDefault("fileUrl", "")));
    row.put("definition", definition);
    return row;
  }
}
