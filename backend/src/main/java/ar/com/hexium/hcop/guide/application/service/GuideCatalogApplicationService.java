package ar.com.hexium.hcop.guide.application.service;

import ar.com.hexium.hcop.guide.application.port.in.GuideCatalogUseCase;
import ar.com.hexium.hcop.guide.application.port.out.GuideFileStore;
import ar.com.hexium.hcop.guide.application.port.out.GuideFileTooLargeException;
import ar.com.hexium.hcop.guide.application.port.out.GuideMetadataPort;
import ar.com.hexium.hcop.guide.application.port.out.GuideStorageException;
import ar.com.hexium.hcop.guide.domain.GuideFileName;
import ar.com.hexium.hcop.guide.domain.GuideMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Biblioteca de guías independiente de HTTP y del sistema de archivos.
 */
public final class GuideCatalogApplicationService implements GuideCatalogUseCase {
  static final long MAX_BYTES = 50L * 1024 * 1024;
  private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

  private final GuideFileStore files;
  private final GuideMetadataPort metadata;

  public GuideCatalogApplicationService(GuideFileStore files, GuideMetadataPort metadata) {
    this.files = files;
    this.metadata = metadata;
  }

  @Override
  public List<GuideView> list(boolean includeInactive) {
    try {
      Map<GuideFileName, GuideMetadata> overrides = new HashMap<>();
      metadata.listAll().forEach(item -> overrides.put(item.fileName(), item));
      return files.list().stream()
          .map(file -> view(file, overrides.get(file.name())))
          .filter(item -> includeInactive || item.active())
          .sorted(Comparator.comparing(GuideView::title, String.CASE_INSENSITIVE_ORDER))
          .toList();
    } catch (GuideStorageException storage) {
      throw new GuideFailure(
          GuideFailure.Type.STORAGE,
          "No se pudo leer la biblioteca de guías.");
    }
  }

  @Override
  public GuideContent open(String rawName) {
    GuideFileName name = name(rawName);
    try {
      GuideFileStore.GuideContent content = files.open(name)
          .orElseThrow(() -> new GuideFailure(
              GuideFailure.Type.NOT_FOUND,
              "Guía no encontrada."));
      return new GuideContent(content.name().value(), content.size(), content.content());
    } catch (GuideStorageException storage) {
      throw new GuideFailure(
          GuideFailure.Type.STORAGE,
          "No se pudo abrir la guía.");
    }
  }

  @Override
  public UploadResult upload(String rawName, InputStream content, long declaredSize) {
    GuideFileName name = name(rawName);
    if (!name.pdf()) {
      closeQuietly(content);
      throw new GuideFailure(GuideFailure.Type.INVALID, "El archivo debe ser PDF.");
    }
    if (declaredSize == 0 || declaredSize > MAX_BYTES) {
      closeQuietly(content);
      throw new GuideFailure(
          GuideFailure.Type.INVALID,
          "El PDF está vacío o supera 50 MB.");
    }
    PushbackInputStream validated = new PushbackInputStream(content, PDF_SIGNATURE.length);
    try {
      byte[] signature = validated.readNBytes(PDF_SIGNATURE.length);
      if (!java.util.Arrays.equals(signature, PDF_SIGNATURE)) {
        validated.close();
        throw new GuideFailure(
            GuideFailure.Type.INVALID,
            "El contenido no es un PDF válido.");
      }
      validated.unread(signature);
      var stored = files.store(name, validated, MAX_BYTES);
      return new UploadResult(stored.name().value(), stored.size(), true);
    } catch (GuideFileTooLargeException tooLarge) {
      throw new GuideFailure(
          GuideFailure.Type.TOO_LARGE,
          "El PDF supera 50 MB.");
    } catch (GuideStorageException storage) {
      throw new GuideFailure(
          GuideFailure.Type.STORAGE,
          "No se pudo guardar la guía.");
    } catch (GuideFailure failure) {
      throw failure;
    } catch (IOException failure) {
      closeQuietly(validated);
      throw new GuideFailure(
          GuideFailure.Type.INVALID,
          "No se pudo leer el PDF.");
    }
  }

  private GuideView view(GuideFileStore.StoredGuide file, GuideMetadata override) {
    String base = file.name().value()
        .replaceFirst("(?i)_blocks?\\.pdf$", "")
        .replaceFirst("(?i)\\.pdf$", "");
    String title = humanize(base);
    return new GuideView(
        file.name().value(),
        override == null ? title : defaultText(override.title(), title),
        override == null ? title : defaultText(override.category(), title),
        override == null ? "Oncología" : defaultText(override.audience(), "Oncología"),
        override == null ? "Guía clínica local" : defaultText(override.source(), "Guía clínica local"),
        override == null ? "" : defaultText(override.version(), ""),
        override == null ? List.of() : override.tags(),
        override == null ? "" : defaultText(override.description(), ""),
        override == null || override.active(),
        override == null ? "" : override.configurationId(),
        override == null ? "" : override.configurationRevision(),
        file.size(),
        file.updatedAt());
  }

  private GuideFileName name(String rawName) {
    try {
      return GuideFileName.fromRaw(rawName);
    } catch (IllegalArgumentException invalid) {
      throw new GuideFailure(GuideFailure.Type.INVALID, "Nombre inválido.");
    }
  }

  private String humanize(String value) {
    String text = value.replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ").strip();
    if (text.isBlank()) return "Guía clínica";
    return text.substring(0, 1).toUpperCase(Locale.forLanguageTag("es-AR")) + text.substring(1);
  }

  private String defaultText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private void closeQuietly(InputStream stream) {
    if (stream == null) return;
    try {
      stream.close();
    } catch (IOException ignored) {
      // El error clínicamente relevante ya está representado por GuideFailure.
    }
  }
}
