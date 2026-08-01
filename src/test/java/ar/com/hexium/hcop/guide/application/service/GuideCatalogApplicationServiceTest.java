package ar.com.hexium.hcop.guide.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.guide.application.port.out.GuideFileStore;
import ar.com.hexium.hcop.guide.application.port.out.GuideFileStore.StoredGuide;
import ar.com.hexium.hcop.guide.application.port.out.GuideMetadataPort;
import ar.com.hexium.hcop.guide.domain.GuideFileName;
import ar.com.hexium.hcop.guide.domain.GuideMetadata;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuideCatalogApplicationServiceTest {
  private final GuideFileStore files = mock(GuideFileStore.class);
  private final GuideMetadataPort metadata = mock(GuideMetadataPort.class);
  private final GuideCatalogApplicationService service =
      new GuideCatalogApplicationService(files, metadata);

  @Test
  void combinesFilesAndVersionedMetadataAndFiltersInactiveGuides() {
    GuideFileName lung = GuideFileName.fromRaw("pulmon.pdf");
    GuideFileName breast = GuideFileName.fromRaw("mama_blocks.pdf");
    when(files.list()).thenReturn(List.of(
        new StoredGuide(lung, 120, Instant.parse("2026-07-30T10:00:00Z")),
        new StoredGuide(breast, 240, Instant.parse("2026-07-30T11:00:00Z"))));
    when(metadata.listAll()).thenReturn(List.of(
        new GuideMetadata(
            lung,
            "Guía de pulmón",
            "Tórax",
            "Oncología",
            "COIR",
            "2026",
            List.of("pulmón", "tratamiento"),
            "Descripción",
            false,
            "8",
            3)));

    assertThat(service.list(false))
        .singleElement()
        .satisfies(item -> {
          assertThat(item.name()).isEqualTo("mama_blocks.pdf");
          assertThat(item.title()).isEqualTo("Mama");
          assertThat(item.configurationId()).isEmpty();
        });
    assertThat(service.list(true))
        .filteredOn(item -> item.name().equals("pulmon.pdf"))
        .singleElement()
        .satisfies(item -> {
          assertThat(item.title()).isEqualTo("Guía de pulmón");
          assertThat(item.site()).isEqualTo("Tórax");
          assertThat(item.tags()).containsExactly("pulmón", "tratamiento");
          assertThat(item.configurationRevision()).isEqualTo(3L);
          assertThat(item.active()).isFalse();
        });
  }

  @Test
  void validatesThePdfSignatureBeforeWriting() {
    byte[] invalid = "not-a-pdf".getBytes(StandardCharsets.US_ASCII);

    assertThatThrownBy(() -> service.upload(
        "archivo.pdf",
        new ByteArrayInputStream(invalid),
        invalid.length))
        .isInstanceOfSatisfying(
            GuideFailure.class,
            failure -> assertThat(failure.code()).isEqualTo("INVALID_GUIDE"));
    verify(files, never()).store(any(), any(), anyLong());
  }

  @Test
  void streamsTheWholeValidatedPdfToTheFilePort() {
    byte[] pdf = "%PDF-1.4\nHCOP".getBytes(StandardCharsets.US_ASCII);
    when(files.store(any(), any(), eq(GuideCatalogApplicationService.MAX_BYTES)))
        .thenAnswer(invocation -> {
          assertThat(((java.io.InputStream) invocation.getArgument(1)).readAllBytes())
              .isEqualTo(pdf);
          return new StoredGuide(
              GuideFileName.fromRaw("guia.pdf"),
              pdf.length,
              Instant.parse("2026-07-30T10:00:00Z"));
        });

    var result = service.upload(
        "guia.pdf",
        new ByteArrayInputStream(pdf),
        pdf.length);

    assertThat(result.name()).isEqualTo("guia.pdf");
    assertThat(result.size()).isEqualTo(pdf.length);
    assertThat(result.replaced()).isTrue();
  }
}
