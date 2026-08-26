package ar.com.hexium.hcop.media.application.port.out;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Filesystem del contenido binario. Valida tamaño/firma de bytes no confiables (protocolo de
 * subida) — lanza {@code MediaFailure} directo (F3.4: antes {@code ApiException}), no es regla de
 * negocio.
 */
public interface ClinicalFileBlobStore {

  StoredBlob writeStudy(UUID id, String extension, InputStream content);

  StoredBlob writeImage(UUID id, String extension, byte[] bytes);

  Path resolve(String storageKey);

  void delete(String storageKey);

  record StoredBlob(String storageKey, long size, String sha256) {
  }
}
