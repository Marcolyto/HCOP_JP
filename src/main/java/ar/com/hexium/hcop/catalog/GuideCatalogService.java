package ar.com.hexium.hcop.catalog;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.configuration.ConfigurationService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class GuideCatalogService {
  private static final long MAX_BYTES = 50L * 1024 * 1024;
  private final Path root;
  private final ConfigurationService configurations;

  public GuideCatalogService(HcopProperties properties, ConfigurationService configurations) {
    this.root = properties.storageRoot().resolve("guides").normalize();
    this.configurations = configurations;
  }

  public List<Map<String, Object>> list(boolean includeInactive) {
    Map<String, Map<String, Object>> overrides = new HashMap<>();
    for (Map<String, Object> item : configurations.list("guide", true)) {
      Object definitionValue = item.get("definition");
      if (!(definitionValue instanceof JsonNode definition)) continue;
      String file = definition.path("fileName").asText("");
      if (!file.isBlank()) overrides.put(file, item);
    }
    List<Map<String, Object>> result = new ArrayList<>();
    if (!Files.isDirectory(root)) return result;
    try (var files = Files.list(root)) {
      files.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
          .forEach(path -> result.add(view(path, overrides.get(path.getFileName().toString()))));
    } catch (IOException exception) {
      throw new IllegalStateException("No se pudo leer la biblioteca de guías.", exception);
    }
    return result.stream()
        .filter(item -> includeInactive || !Boolean.FALSE.equals(item.get("active")))
        .sorted(Comparator.comparing(item -> String.valueOf(item.get("title")), String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  public Path file(String rawName) {
    String name = safeName(rawName);
    Path file = root.resolve(name).normalize();
    if (!file.startsWith(root) || !Files.isRegularFile(file)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Guía no encontrada.");
    }
    return file;
  }

  public Map<String, Object> store(String rawName, InputStream upload, long declaredSize) {
    String name = safeName(rawName);
    if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El archivo debe ser PDF.");
    }
    if (declaredSize == 0 || declaredSize > MAX_BYTES) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El PDF está vacío o supera 50 MB.");
    }
    Path target = root.resolve(name).normalize();
    if (!target.startsWith(root)) throw new ApiException(HttpStatus.BAD_REQUEST, "Nombre inválido.");
    try {
      Files.createDirectories(root);
      Path temporary = Files.createTempFile(root, "guide-", ".part");
      try (var input = upload;
           var output = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        byte[] signature = new byte[5];
        int signatureCount = 0;
        while ((read = input.read(buffer)) >= 0) {
          total += read;
          if (total > MAX_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "El PDF supera 50 MB.");
          if (signatureCount < 5) {
            int count = Math.min(read, 5 - signatureCount);
            System.arraycopy(buffer, 0, signature, signatureCount, count);
            signatureCount += count;
          }
          output.write(buffer, 0, read);
        }
        if (signatureCount < 5 || !"%PDF-".equals(new String(signature, java.nio.charset.StandardCharsets.US_ASCII))) {
          throw new ApiException(HttpStatus.BAD_REQUEST, "El contenido no es un PDF válido.");
        }
      } catch (RuntimeException | IOException failure) {
        Files.deleteIfExists(temporary);
        throw failure;
      }
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      return Map.of("ok", true, "name", name, "size", Files.size(target), "replaced", true);
    } catch (IOException exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la guía.");
    }
  }

  private Map<String, Object> view(Path path, Map<String, Object> override) {
    String name = path.getFileName().toString();
    JsonNode definition = override != null && override.get("definition") instanceof JsonNode node ? node : null;
    String base = name.replaceFirst("(?i)_blocks?\\.pdf$", "").replaceFirst("(?i)\\.pdf$", "");
    String title = humanize(base);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("name", name);
    result.put("title", override == null ? title : override.getOrDefault("name", title));
    result.put("site", definition == null ? title : definition.path("category").asText(title));
    result.put("audience", definition == null ? "Oncología" : definition.path("audience").asText("Oncología"));
    result.put("source", definition == null ? "Guía clínica local" : definition.path("source").asText("Guía clínica local"));
    result.put("version", definition == null ? "" : definition.path("version").asText(""));
    result.put("tags", definition == null ? List.of() : definition.path("tags"));
    result.put("description", override == null ? "" : override.getOrDefault("description", ""));
    result.put("active", override == null || !Boolean.FALSE.equals(override.get("active")));
    result.put("configurationId", override == null ? "" : override.getOrDefault("id", ""));
    result.put("configurationRevision", override == null ? "" : override.getOrDefault("revision", ""));
    result.put("url", "/api/guides/file?name=" + java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8));
    try {
      result.put("size", Files.size(path));
      result.put("updatedAt", Files.getLastModifiedTime(path).toInstant().toString());
    } catch (IOException ignored) {
      result.put("size", 0);
      result.put("updatedAt", Instant.EPOCH.toString());
    }
    return result;
  }

  private String safeName(String rawName) {
    String value = Path.of(rawName == null ? "" : rawName).getFileName().toString()
        .replaceAll("[^\\p{L}\\p{N}._ ()-]", "_");
    if (value.isBlank() || value.length() > 240) throw new ApiException(HttpStatus.BAD_REQUEST, "Nombre inválido.");
    return value;
  }

  private String humanize(String value) {
    String text = value.replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ").trim();
    if (text.isBlank()) return "Guía clínica";
    return text.substring(0, 1).toUpperCase(Locale.forLanguageTag("es-AR")) + text.substring(1);
  }
}
