package ar.com.hexium.hcop.media.infrastructure.persistence;

import ar.com.hexium.hcop.platform.HcopProperties;
import ar.com.hexium.hcop.media.application.port.out.ClinicalFileBlobStore;
import ar.com.hexium.hcop.media.application.service.MediaFailure;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class FilesystemClinicalFileBlobStore implements ClinicalFileBlobStore {
  private final HcopProperties properties;
  private Path studyRoot;
  private Path imageRoot;

  public FilesystemClinicalFileBlobStore(HcopProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  void initializeStorage() throws IOException {
    studyRoot = properties.storageRoot().resolve("studies").toAbsolutePath().normalize();
    imageRoot = properties.storageRoot().resolve("images").toAbsolutePath().normalize();
    Files.createDirectories(studyRoot);
    Files.createDirectories(imageRoot);
  }

  @Override
  public StoredBlob writeStudy(UUID id, String extension, InputStream content) {
    String storageKey = "studies/" + id + extension;
    Path destination = resolve(studyRoot, id + extension);
    Path temporary = resolve(studyRoot, ".upload-" + id + ".tmp");
    try {
      CopyResult copied = stream(content, temporary, properties.maxStudyBytes());
      validateSignature(temporary, extension, copied.size());
      moveAtomic(temporary, destination);
      return new StoredBlob(storageKey, copied.size(), copied.sha256());
    } catch (IOException error) {
      deleteQuietly(temporary);
      throw new MediaFailure(MediaFailure.Type.INTERNAL, "No se pudo guardar el archivo clínico.");
    } catch (RuntimeException error) {
      deleteQuietly(temporary);
      throw error;
    }
  }

  @Override
  public StoredBlob writeImage(UUID id, String extension, byte[] bytes) {
    if (bytes == null || bytes.length < 4 || bytes.length > properties.maxImageBytes()) {
      throw new MediaFailure(MediaFailure.Type.TOO_LARGE, "La imagen supera el límite permitido.");
    }
    String storageKey = "images/" + id + extension;
    Path destination = resolve(imageRoot, id + extension);
    try {
      Files.write(destination, bytes);
      validateSignature(destination, extension, bytes.length);
      return new StoredBlob(storageKey, bytes.length, sha256(bytes));
    } catch (IOException error) {
      deleteQuietly(destination);
      throw new MediaFailure(MediaFailure.Type.INTERNAL, "No se pudo guardar la imagen.");
    } catch (RuntimeException error) {
      deleteQuietly(destination);
      throw error;
    }
  }

  @Override
  public Path resolve(String storageKey) {
    Path root = storageKey.startsWith("images/") ? imageRoot : studyRoot;
    String name = storageKey.substring(storageKey.indexOf('/') + 1);
    Path path = resolve(root, name);
    if (!Files.isRegularFile(path)) throw new MediaFailure(MediaFailure.Type.NOT_FOUND, "Archivo no encontrado.");
    return path;
  }

  @Override
  public void delete(String storageKey) {
    Path root = storageKey.startsWith("images/") ? imageRoot : studyRoot;
    deleteQuietly(resolve(root, storageKey.substring(storageKey.indexOf('/') + 1)));
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
        if (total > limit) throw new MediaFailure(MediaFailure.Type.TOO_LARGE, "El archivo supera el límite de 250 MB.");
        output.write(buffer, 0, read);
      }
    }
    if (total == 0) throw new MediaFailure(MediaFailure.Type.INVALID, "El archivo está vacío.");
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
      case ".gif" -> starts(header, "GIF8".getBytes(StandardCharsets.US_ASCII));
      case ".webp" -> starts(header, "RIFF".getBytes(StandardCharsets.US_ASCII));
      case ".bmp" -> starts(header, "BM".getBytes(StandardCharsets.US_ASCII));
      case ".tif", ".tiff" -> starts(header, new byte[]{0x49, 0x49}) || starts(header, new byte[]{0x4d, 0x4d});
      case ".pdf" -> starts(header, "%PDF-".getBytes(StandardCharsets.US_ASCII));
      case ".docx", ".pptx" -> starts(header, new byte[]{0x50, 0x4b});
      case ".doc", ".ppt" -> starts(header, new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0});
      default -> size > 8;
    };
    if (!valid) {
      throw new MediaFailure(MediaFailure.Type.UNSUPPORTED_FORMAT, "El contenido no coincide con el formato del archivo.");
    }
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

  private Path resolve(Path root, String name) {
    Path resolved = root.resolve(name).normalize();
    if (!resolved.getParent().equals(root)) throw new MediaFailure(MediaFailure.Type.INVALID, "Nombre de archivo inválido.");
    return resolved;
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
}
