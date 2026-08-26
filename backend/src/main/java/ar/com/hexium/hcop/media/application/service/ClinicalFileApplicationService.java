package ar.com.hexium.hcop.media.application.service;

import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.media.application.port.in.ClinicalFileUseCase;
import ar.com.hexium.hcop.media.application.port.out.ClinicalFileBlobStore;
import ar.com.hexium.hcop.media.application.port.out.ClinicalFileBlobStore.StoredBlob;
import ar.com.hexium.hcop.media.application.port.out.ClinicalFileStore;
import ar.com.hexium.hcop.media.application.port.out.ClinicalFileStore.NewClinicalFile;
import ar.com.hexium.hcop.media.application.port.out.PatientLookupPort;
import ar.com.hexium.hcop.media.domain.ClinicalFile;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClinicalFileApplicationService implements ClinicalFileUseCase {
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

  private final ClinicalFileStore store;
  private final ClinicalFileBlobStore blobs;
  private final PatientLookupPort patients;
  private final AuthService auth;
  private final Clock clock;

  public ClinicalFileApplicationService(
      ClinicalFileStore store, ClinicalFileBlobStore blobs, PatientLookupPort patients,
      AuthService auth, Clock clock) {
    this.store = store;
    this.blobs = blobs;
    this.patients = patients;
    this.auth = auth;
    this.clock = clock;
  }

  @Override
  public StudyUpload uploadStudy(UploadStudyCommand command) {
    patients.requireExists(command.patientId());
    String originalName = safeName(command.requestedName());
    String extension = extension(originalName);
    if (!STUDY_EXTENSIONS.contains(extension)) {
      throw new MediaFailure(MediaFailure.Type.UNSUPPORTED_FORMAT, "Formato de estudio no permitido.");
    }
    String declared = command.declaredContentType();
    String contentType = CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    if (declared != null && !declared.isBlank() && !"application/octet-stream".equals(declared)) {
      contentType = declared.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }
    UUID id = UUID.randomUUID();
    StoredBlob blob = blobs.writeStudy(id, extension, command.content());
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("studyId", clean(command.studyId(), 120));
    metadata.put("category", category(extension));
    metadata.put("previewable", contentType.startsWith("image/") || "application/pdf".equals(contentType)
        || contentType.startsWith("video/"));
    Instant now = clock.instant();
    String deleteToken = UUID.randomUUID().toString().replace("-", "")
        + UUID.randomUUID().toString().replace("-", "");
    String deleteTokenHash = auth.sha256(deleteToken);
    try {
      ClinicalFile stored = store.insert(new NewClinicalFile(
          id, command.patientId(), "", "study", originalName, blob.storageKey(), contentType,
          blob.size(), blob.sha256(), metadata, command.actorId().value(), deleteTokenHash,
          now.plus(Duration.ofHours(24)), now));
      return new StudyUpload(stored, deleteToken);
    } catch (RuntimeException databaseError) {
      blobs.delete(blob.storageKey());
      throw databaseError;
    }
  }

  @Override
  public ClinicalFile storeImage(StoreImageCommand command) {
    String normalizedType = command.contentType() == null ? "" : command.contentType().toLowerCase(Locale.ROOT);
    String extension = switch (normalizedType) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/gif" -> ".gif";
      case "image/webp" -> ".webp";
      case "image/bmp" -> ".bmp";
      case "image/tiff" -> ".tiff";
      default -> throw new MediaFailure(MediaFailure.Type.UNSUPPORTED_FORMAT, "Tipo de imagen no permitido.");
    };
    UUID id = UUID.randomUUID();
    StoredBlob blob = blobs.writeImage(id, extension, command.bytes());
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("kind", clean(command.kind(), 32));
    Instant now = clock.instant();
    try {
      return store.insert(new NewClinicalFile(
          id, null, "", "image", safeName(command.fileName() + extension), blob.storageKey(),
          normalizedType, blob.size(), blob.sha256(), metadata, command.actorId().value(),
          command.sessionId(), now.plus(Duration.ofHours(24)), now));
    } catch (RuntimeException databaseError) {
      blobs.delete(blob.storageKey());
      throw databaseError;
    }
  }

  @Override
  public ClinicalFile requireStudy(String name) {
    return requireByStorageName("studies", name);
  }

  @Override
  public ClinicalFile requireImage(String name) {
    return requireByStorageName("images", name);
  }

  @Override
  public Optional<ClinicalFile> findLatestByTreatment(String treatmentId, String kind) {
    return store.findLatestByTreatment(treatmentId, kind);
  }

  @Override
  public Path resolvePath(ClinicalFile file) {
    return blobs.resolve(file.storageKey());
  }

  @Override
  public void deleteStudy(String name, String deleteToken) {
    ClinicalFile file = requireStudy(name);
    String tokenHash = auth.sha256(deleteToken);
    if (!store.deleteGranted(file.id(), tokenHash, clock.instant())) {
      throw new MediaFailure(
          MediaFailure.Type.FORBIDDEN, "Esta carga ya no se puede eliminar desde la sesión actual.");
    }
    blobs.delete(file.storageKey());
  }

  @Override
  public void discardImage(ClinicalFile file) {
    try {
      store.delete(file.id());
    } finally {
      blobs.delete(file.storageKey());
    }
  }

  private ClinicalFile requireByStorageName(String kind, String name) {
    String safe = safeStorageName(name);
    return store.findByStorageKey(kind + "/" + safe)
        .orElseThrow(() -> new MediaFailure(MediaFailure.Type.NOT_FOUND, "Archivo no encontrado."));
  }

  private String safeName(String value) {
    String name = Path.of(value == null || value.isBlank() ? "archivo" : value).getFileName().toString()
        .replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").trim();
    if (name.length() > 240) name = name.substring(name.length() - 240);
    if (name.isBlank()) throw new MediaFailure(MediaFailure.Type.INVALID, "Nombre de archivo inválido.");
    return name;
  }

  private String safeStorageName(String name) {
    if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,180}")) {
      throw new MediaFailure(MediaFailure.Type.INVALID, "Nombre de archivo inválido.");
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
}
