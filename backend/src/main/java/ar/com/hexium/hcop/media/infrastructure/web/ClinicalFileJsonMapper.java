package ar.com.hexium.hcop.media.infrastructure.web;

import ar.com.hexium.hcop.media.domain.ClinicalFile;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ClinicalFileJsonMapper {
  private final ObjectMapper mapper;

  public ClinicalFileJsonMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public Map<String, Object> studyView(ClinicalFile file, String deleteToken) {
    String name = storedName(file);
    var metadata = mapper.valueToTree(file.metadata());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", "file-" + file.id());
    result.put("studyId", metadata.path("studyId").asText(""));
    result.put("patientId", file.patientId() == null ? "" : Long.toString(file.patientId()));
    result.put("fileName", file.originalName());
    result.put("storedName", name);
    result.put("contentType", file.contentType());
    result.put("size", file.size());
    result.put("sha256", file.sha256());
    result.put("category", metadata.path("category").asText(""));
    result.put("previewable", metadata.path("previewable").asBoolean(false));
    result.put("url", "/api/media/studies/" + name);
    result.put("uploadedAt", file.createdAt().toString());
    result.put("deleteToken", deleteToken == null ? "" : deleteToken);
    result.put("deleteExpiresAt", file.deletableUntil() == null ? null : file.deletableUntil().toString());
    return result;
  }

  public Map<String, Object> imageView(ClinicalFile file) {
    String name = storedName(file);
    var metadata = mapper.valueToTree(file.metadata());
    return Map.of(
        "url", "/api/media/images/" + name,
        "name", name,
        "mime", file.contentType(),
        "size", file.size(),
        "kind", metadata.path("kind").asText("original"));
  }

  private String storedName(ClinicalFile file) {
    return file.storageKey().substring(file.storageKey().indexOf('/') + 1);
  }
}
