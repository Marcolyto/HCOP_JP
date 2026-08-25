package ar.com.hexium.hcop.media;

import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.media.ClinicalFileRepository.StoredFile;
import ar.com.hexium.hcop.patient.PatientService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ClinicalFileService {
  private static final Set<String> STUDY_EXTENSIONS = Set.of(
      ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".tif", ".tiff",
      ".pdf", ".doc", ".docx", ".ppt", ".pptx", ".mp4", ".webm", ".mov", ".avi", ".mkv");
  private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
      Map.entry(".jpg", "image/jpeg"), Map.entry(".jpeg", "image/jpeg"),
      Map.entry(".png", "image/png"), Map.entry(".gif", "image/gif"),
      Map.entry(".webp", "image/webp"), Map.entry(".bmp", "image/bmp"),
      Map.entry(".tif", "image/tiff"), Map.entry(".tiff", "image/tiff"),
      Map.entry(".pdf", "application/pdf"), Map.entry(".doc", "application/msword"),
      Map.entry(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
      Map.entry(".ppt", "application/vnd.ms-powerpoint"),
      Map.entry(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
      Map.entry(".mp4", "video/mp4"), Map.entry(".webm", "video/webm"),
      Map.entry(".mov", "video/quicktime"), Map.entry(".avi", "video/x-msvideo"),
      Map.entry(".mkv", "video/x-matroska"));
  private final ClinicalFileRepository files;
  private final PatientService patients;
  private final AuthService auth;
  private final HcopProperties properties;
  private final ObjectMapper mapper;
  private final Clock clock;
  private Path studyRoot;
  private Path imageRoot;

  public ClinicalFileService(
      ClinicalFileRepository files,
      PatientService patients,
      AuthService auth,
      HcopProperties properties,
      ObjectMapper mapper,
      Clock clock) {
    this.files = files;
    this.patients = patients;
    this.auth = auth;
    this.properties = properties;
    this.mapper = mapper;
    this.clock = clock;
  }

  @PostConstruct
  void initializeStorage() throws IOException {
    studyRoot = properties.storageRoot().resolve("studies").toAbsolutePath().normalize();
    imageRoot = properties.storageRoot().resolve("images").toAbsolutePath().normalize();
    Files.createDirectories(studyRoot);
    Files.createDirectories(imageRoot);
  }

  public StudyUpload uploadStudy(
      HttpServletRequest request, long patientId, String studyId, String requestedName,
      SessionPrincipal actor, String rawSessionToken) {
    patients.require(patientId);
    String originalName = safeName(requestedName);
    String extension = extension(originalName);
    if (!STUDY_EXTENSIONS.contains(extension)) {
      throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de estudio no permitido.");
    }
    String declared = request.getContentType();
    String contentType = CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    if (declared != null && !declared.isBlank() && !"application/octet-stream".equals(declared)) {
      contentType = declared.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }
    UUID id = UUID.randomUUID();
    String storageKey = "studies/" + id + extension;
    Path destination = resolve(studyRoot, id + extension);
    Path temporary = resolve(studyRoot, ".upload-" + id + ".tmp");
    CopyResult copied;
    try {
      copied = stream(request.getInputStream(), temporary, properties.maxStudyBytes());
      validateSignature(temporary, extension, copied.size());
      moveAtomic(temporary, destination);
    } catch (IOException error) {
      deleteQuietly(temporary);
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el archivo clínico.");
    } catch (RuntimeException error) {
      deleteQuietly(temporary);
      throw error;
    }
    ObjectNode metadata = mapper.createObjectNode();
    metadata.put("studyId", clean(studyId, 120));
    metadata.put("category", category(extension));
    metadata.put("previewable", contentType.startsWith("image/") || "application/pdf".equals(contentType) ||
        contentType.startsWith("video/"));
    Instant now = clock.instant();
    String deleteToken = UUID.randomUUID().toString().replace("-", "") +
        UUID.randomUUID().toString().replace("-", "");
    String deleteTokenHash = auth.sha256(deleteToken);
    try {
      StoredFile stored = files.insert(
          id, patientId, "", "study", originalName, storageKey, contentType,
          copied.size(), copied.sha256(), metadata, actor.userId(), deleteTokenHash,
          now.plus(Duration.ofHours(24)), now);
      return new StudyUpload(stored, deleteToken);
    } catch (RuntimeException databaseError) {
      deleteQuietly(destination);
      throw databaseError;
    }
  }

  public StoredFile storeImage(
      String fileName, byte[] bytes, String contentType, String kind,
      SessionPrincipal actor, String rawSessionToken) {
    if (bytes.length < 4 || bytes.length > properties.maxImageBytes()) {
      throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen supera el límite permitido.");
    }
    String normalizedType = contentType.toLowerCase(Locale.ROOT);
    String extension = switch (normalizedType) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/gif" -> ".gif";
      case "image/webp" -> ".webp";
      case "image/bmp" -> ".bmp";
      case "image/tiff" -> ".tiff";
      default -> throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de imagen no permitido.");
    };
    UUID id = UUID.randomUUID();
    Path destination = resolve(imageRoot, id + extension);
    try {
      Files.write(destination, bytes);
      validateSignature(destination, extension, bytes.length);
    } catch (IOException error) {
      deleteQuietly(destination);
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la imagen.");
    }
    String sha = sha256(bytes);
    ObjectNode metadata = mapper.createObjectNode();
    metadata.put("kind", clean(kind, 32));
    Instant now = clock.instant();
    try {
      return files.insert(
          id, null, "", "image", safeName(fileName + extension),
          "images/" + id + extension, normalizedType, bytes.length, sha, metadata,
          actor.userId(), auth.sha256(rawSessionToken), now.plus(Duration.ofHours(24)), now);
    } catch (RuntimeException databaseError) {
      deleteQuietly(destination);
      throw databaseError;
    }
  }

  public StoredFile requireByStorageName(String kind, String name) {
    String safe = safeStorageName(name);
    return files.findByStorageKey(kind + "/" + safe)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Archivo no encontrado."));
  }

  public Path path(StoredFile file) {
    Path root = file.storageKey().startsWith("images/") ? imageRoot : studyRoot;
    String name = file.storageKey().substring(file.storageKey().indexOf('/') + 1);
    Path path = resolve(root, name);
    if (!Files.isRegularFile(path)) throw new ApiException(HttpStatus.NOT_FOUND, "Archivo no encontrado.");
    return path;
  }

  public void deleteStudy(String name, String deleteToken) {
    StoredFile file = requireByStorageName("studies", name);
    String tokenHash = auth.sha256(deleteToken);
    if (!files.deleteGranted(file.id(), tokenHash, clock.instant())) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "Esta carga ya no se puede eliminar desde la sesión actual.");
    }
    deleteQuietly(pathFor(file));
  }

  public Map<String, Object> studyView(StoredFile file, String deleteToken) {
    String name = file.storageKey().substring(file.storageKey().indexOf('/') + 1);
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("id", "file-" + file.id());
    result.put("studyId", file.metadata().path("studyId").asText(""));
    result.put("patientId", file.patientId() == null ? "" : Long.toString(file.patientId()));
    result.put("fileName", file.originalName());
    result.put("storedName", name);
    result.put("contentType", file.contentType());
    result.put("size", file.size());
    result.put("sha256", file.sha256());
    result.put("category", file.metadata().path("category").asText(""));
    result.put("previewable", file.metadata().path("previewable").asBoolean(false));
    result.put("url", "/api/media/studies/" + name);
    result.put("uploadedAt", file.createdAt().toString());
    result.put("deleteToken", deleteToken == null ? "" : deleteToken);
    result.put("deleteExpiresAt", file.deletableUntil() == null ? null : file.deletableUntil().toString());
    return result;
  }

  public Map<String, Object> imageView(StoredFile file) {
    String name = file.storageKey().substring(file.storageKey().indexOf('/') + 1);
    return Map.of(
        "url", "/api/media/images/" + name,
        "name", name,
        "mime", file.contentType(),
        "size", file.size(),
        "kind", file.metadata().path("kind").asText("original"));
  }

  void discardImage(StoredFile file) {
    try {
      files.delete(file.id());
    } finally {
      deleteQuietly(pathFor(file));
    }
  }

  private CopyResult stream(InputStream source, Path destination, long limit) throws IOException {
    MessageDigest digest = digest();
    long total = 0;
    byte[] buffer = new byte[64 * 1024];
    try (DigestInputStream input = new DigestInputStream(source, digest);
         var output = Files.newOutputStream(destination)) {
      int read;
      while ((read = input.read(buffer)) >= 0) {
        if (read == 0) continue;
        total += read;
        if (total > limit) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el límite de 250 MB.");
        output.write(buffer, 0, read);
      }
    }
    if (total == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "El archivo está vacío.");
    return new CopyResult(total, HexFormat.of().formatHex(digest.digest()));
  }

  private void validateSignature(Path file, String extension, long size) throws IOException {
    byte[] header;
    try (InputStream input = Files.newInputStream(file)) {
      header = input.readNBytes(32);
    }
    boolean valid = switch (extension) {
      case ".jpg", ".jpeg" -> header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8;
      case ".png" -> starts(header, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});
      case ".gif" -> starts(header, "GIF8".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      case ".webp" -> starts(header, "RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      case ".bmp" -> starts(header, "BM".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      case ".tif", ".tiff" -> starts(header, new byte[]{0x49, 0x49}) || starts(header, new byte[]{0x4d, 0x4d});
      case ".pdf" -> starts(header, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      case ".docx", ".pptx" -> starts(header, new byte[]{0x50, 0x4b});
      case ".doc", ".ppt" -> starts(header, new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0});
      default -> size > 8;
    };
    if (!valid) throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El contenido no coincide con el formato del archivo.");
  }

  private boolean starts(byte[] value, byte[] prefix) {
    if (value.length < prefix.length) return false;
    for (int index = 0; index < prefix.length; index++) if (value[index] != prefix[index]) return false;
    return true;
  }

  private void moveAtomic(Path source, Path destination) throws IOException {
    try {
      Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException unsupported) {
      Files.move(source, destination);
    }
  }

  private Path pathFor(StoredFile file) {
    Path root = file.storageKey().startsWith("images/") ? imageRoot : studyRoot;
    return resolve(root, file.storageKey().substring(file.storageKey().indexOf('/') + 1));
  }

  private Path resolve(Path root, String name) {
    Path resolved = root.resolve(name).normalize();
    if (!resolved.getParent().equals(root)) throw new ApiException(HttpStatus.BAD_REQUEST, "Nombre de archivo inválido.");
    return resolved;
  }

  private String safeName(String value) {
    String name = Path.of(value == null || value.isBlank() ? "archivo" : value).getFileName().toString()
        .replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").trim();
    if (name.length() > 240) name = name.substring(name.length() - 240);
    if (name.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Nombre de archivo inválido.");
    return name;
  }

  private String safeStorageName(String name) {
    if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,180}")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Nombre de archivo inválido.");
    }
    return name;
  }

  private String extension(String name) {
    int separator = name.lastIndexOf('.');
    return separator < 0 ? "" : name.substring(separator).toLowerCase(Locale.ROOT);
  }

  private String category(String extension) {
    if (Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".tif", ".tiff").contains(extension)) return "image";
    if (".pdf".equals(extension)) return "pdf";
    if (Set.of(".doc", ".docx").contains(extension)) return "document";
    if (Set.of(".ppt", ".pptx").contains(extension)) return "presentation";
    return "video";
  }

  private String clean(String value, int max) {
    String text = value == null ? "" : value.replaceAll("\\p{Cntrl}", "").trim();
    return text.length() > max ? text.substring(0, max) : text;
  }

  private MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private String sha256(byte[] bytes) {
    return HexFormat.of().formatHex(digest().digest(bytes));
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
    }
  }

  private record CopyResult(long size, String sha256) {
  }

  public record StudyUpload(StoredFile file, String deleteToken) {
  }
}
