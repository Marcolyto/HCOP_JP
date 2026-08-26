package ar.com.hexium.hcop.media.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.media.application.port.in.ClinicalFileUseCase.StoreImageCommand;
import ar.com.hexium.hcop.media.application.port.in.ClinicalFileUseCase.UploadStudyCommand;
import ar.com.hexium.hcop.media.application.port.out.ClinicalFileBlobStore;
import ar.com.hexium.hcop.media.application.port.out.ClinicalFileBlobStore.StoredBlob;
import ar.com.hexium.hcop.media.application.port.out.ClinicalFileStore;
import ar.com.hexium.hcop.media.application.port.out.ClinicalFileStore.NewClinicalFile;
import ar.com.hexium.hcop.media.application.port.out.PatientLookupPort;
import ar.com.hexium.hcop.media.domain.ClinicalFile;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClinicalFileApplicationServiceTest {
  private final ClinicalFileStore store = mock(ClinicalFileStore.class);
  private final ClinicalFileBlobStore blobs = mock(ClinicalFileBlobStore.class);
  private final PatientLookupPort patients = mock(PatientLookupPort.class);
  private final AuthService auth = mock(AuthService.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC);
  private ClinicalFileApplicationService service;

  @BeforeEach
  void setUp() {
    service = new ClinicalFileApplicationService(store, blobs, patients, auth, clock);
  }

  @Test
  void rechazaExtensionDeEstudioNoPermitida() {
    var command = new UploadStudyCommand(
        1L, "study-1", "archivo.exe", null, new ByteArrayInputStream(new byte[0]), UserId.of(1), "session");

    assertThatThrownBy(() -> service.uploadStudy(command))
        .isInstanceOf(MediaFailure.class)
        .satisfies(error -> assertThat(((MediaFailure) error).type())
            .isEqualTo(MediaFailure.Type.UNSUPPORTED_FORMAT));

    verify(patients).requireExists(1L);
    verify(blobs, never()).writeStudy(any(), any(), any());
  }

  @Test
  void subeUnEstudioValidoYPersisteElRegistro() {
    when(blobs.writeStudy(any(), org.mockito.ArgumentMatchers.eq(".png"), any()))
        .thenReturn(new StoredBlob("studies/x.png", 10L, "sha"));
    ClinicalFile stored = file();
    when(store.insert(any(NewClinicalFile.class))).thenReturn(stored);
    when(auth.sha256(org.mockito.ArgumentMatchers.anyString())).thenReturn("hash");
    var command = new UploadStudyCommand(
        1L, "study-1", "archivo.png", "image/png", new ByteArrayInputStream(new byte[]{1, 2, 3}),
        UserId.of(9), "session");

    var upload = service.uploadStudy(command);

    assertThat(upload.file()).isEqualTo(stored);
    assertThat(upload.deleteToken()).isNotBlank();
  }

  @Test
  void rechazaTipoDeImagenNoPermitido() {
    var command = new StoreImageCommand("foto", new byte[]{1, 2, 3, 4}, "image/svg+xml", "original", UserId.of(1), "s");

    assertThatThrownBy(() -> service.storeImage(command))
        .isInstanceOf(MediaFailure.class)
        .satisfies(error -> assertThat(((MediaFailure) error).type())
            .isEqualTo(MediaFailure.Type.UNSUPPORTED_FORMAT));
  }

  @Test
  void rechazaNombreDeArchivoInvalidoAlBuscar() {
    assertThatThrownBy(() -> service.requireStudy("../etc/passwd"))
        .isInstanceOf(MediaFailure.class)
        .satisfies(error -> assertThat(((MediaFailure) error).type()).isEqualTo(MediaFailure.Type.INVALID));
  }

  @Test
  void archivoInexistenteEsNotFound() {
    when(store.findByStorageKey("studies/x.png")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireStudy("x.png"))
        .isInstanceOf(MediaFailure.class)
        .satisfies(error -> assertThat(((MediaFailure) error).type()).isEqualTo(MediaFailure.Type.NOT_FOUND));
  }

  @Test
  void borradoSinTokenValidoEsForbidden() {
    ClinicalFile stored = file();
    when(store.findByStorageKey("studies/x.png")).thenReturn(Optional.of(stored));
    when(auth.sha256("token")).thenReturn("hash");
    when(store.deleteGranted(stored.id(), "hash", clock.instant())).thenReturn(false);

    assertThatThrownBy(() -> service.deleteStudy("x.png", "token"))
        .isInstanceOf(MediaFailure.class)
        .satisfies(error -> assertThat(((MediaFailure) error).type()).isEqualTo(MediaFailure.Type.FORBIDDEN));
  }

  private ClinicalFile file() {
    return new ClinicalFile(
        UUID.fromString("00000000-0000-0000-0000-000000000012"), 1L, "", "study",
        "archivo.png", "studies/x.png", "image/png", 10L, "sha", Map.of(),
        9L, clock.instant(), "hash", clock.instant());
  }
}
