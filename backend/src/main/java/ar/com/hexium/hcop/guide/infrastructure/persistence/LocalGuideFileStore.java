package ar.com.hexium.hcop.guide.infrastructure.persistence;

import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.guide.application.port.out.GuideFileStore;
import ar.com.hexium.hcop.guide.application.port.out.GuideFileTooLargeException;
import ar.com.hexium.hcop.guide.application.port.out.GuideStorageException;
import ar.com.hexium.hcop.guide.domain.GuideFileName;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LocalGuideFileStore implements GuideFileStore {
  private final Path root;

  public LocalGuideFileStore(HcopProperties properties) {
    this.root = properties.storageRoot().resolve("guides").toAbsolutePath().normalize();
  }

  @Override
  public List<StoredGuide> list() {
    if (!Files.isDirectory(root)) return List.of();
    try (var files = Files.list(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
          .map(this::stored)
          .toList();
    } catch (IOException failure) {
      throw new GuideStorageException("No se pudo leer la biblioteca de guías.", failure);
    }
  }

  @Override
  public Optional<GuideContent> open(GuideFileName name) {
    Path file = resolve(name);
    if (!Files.isRegularFile(file)) return Optional.empty();
    try {
      return Optional.of(new GuideContent(
          name,
          Files.size(file),
          Files.newInputStream(file, StandardOpenOption.READ)));
    } catch (IOException failure) {
      throw new GuideStorageException("No se pudo abrir la guía.", failure);
    }
  }

  @Override
  public StoredGuide store(GuideFileName name, InputStream content, long maximumBytes) {
    Path target = resolve(name);
    Path temporary = null;
    try {
      Files.createDirectories(root);
      temporary = Files.createTempFile(root, "guide-", ".part");
      try (InputStream input = content;
           var output = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
          total += read;
          if (total > maximumBytes) throw new GuideFileTooLargeException();
          output.write(buffer, 0, read);
        }
      }
      Files.move(
          temporary,
          target,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
      return stored(target);
    } catch (GuideFileTooLargeException tooLarge) {
      deleteQuietly(temporary);
      throw tooLarge;
    } catch (IOException failure) {
      deleteQuietly(temporary);
      throw new GuideStorageException("No se pudo guardar la guía.", failure);
    }
  }

  private StoredGuide stored(Path path) {
    try {
      return new StoredGuide(
          new GuideFileName(path.getFileName().toString()),
          Files.size(path),
          Files.getLastModifiedTime(path).toInstant());
    } catch (IOException failure) {
      return new StoredGuide(
          new GuideFileName(path.getFileName().toString()),
          0,
          Instant.EPOCH);
    }
  }

  private Path resolve(GuideFileName name) {
    Path file = root.resolve(name.value()).normalize();
    if (!file.startsWith(root)) {
      throw new GuideStorageException("Nombre de guía fuera del almacenamiento.", null);
    }
    return file;
  }

  private void deleteQuietly(Path path) {
    if (path == null) return;
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Se conserva el error original de escritura o tamaño.
    }
  }
}
