package ar.com.hexium.hcop.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.configuration.ConfigurationService;
import ar.com.hexium.hcop.media.ClinicalFileRepository.StoredFile;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class StudyTemplateControllerTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void creaArchivoYConfiguracionCompletaEnUnaSolaOperacion() throws Exception {
    Fixture fixture = fixture();
    when(fixture.configurations.create(eq("study-template"), any(JsonNode.class), eq(7L)))
        .thenReturn(Map.of("id", "12", "name", "Pelvis"));

    var response = fixture.controller.create(
        "Pelvis", "pelvis", "sagital, femenina", "Institución", "Equipo clínico",
        "Propia", "Descripción", "https://example.test/source", "https://example.test/license",
        1, "pelvis.png", fixture.request);

    ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
    verify(fixture.configurations).create(eq("study-template"), body.capture(), eq(7L));
    JsonNode definition = body.getValue().path("definition");
    assertThat(definition.path("attribution").asText()).isEqualTo("Equipo clínico");
    assertThat(definition.path("tags").size()).isEqualTo(2);
    assertThat(definition.path("tags").get(0).asText()).isEqualTo("sagital");
    assertThat(response.getStatusCode().value()).isEqualTo(201);
    verify(fixture.files, never()).discardImage(fixture.image);
  }

  @Test
  void eliminaLaImagenSiNoPuedeCrearLaConfiguracion() {
    Fixture fixture = fixture();
    IllegalStateException databaseFailure = new IllegalStateException("database unavailable");
    when(fixture.configurations.create(eq("study-template"), any(JsonNode.class), eq(7L)))
        .thenThrow(databaseFailure);

    assertThatThrownBy(() -> fixture.controller.create(
        "Pelvis", "pelvis", "", "Institución", "", "Propia", "", "", "",
        1, "pelvis.png", fixture.request))
        .isSameAs(databaseFailure);

    verify(fixture.files).discardImage(fixture.image);
  }

  private Fixture fixture() {
    ConfigurationService configurations = mock(ConfigurationService.class);
    ClinicalFileService files = mock(ClinicalFileService.class);
    AuthContext auth = mock(AuthContext.class);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setContent(new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});
    request.setContentType("image/png");
    SessionPrincipal actor = new SessionPrincipal(
        7L, "oncologo", "", "Oncólogo", "Oncología", "MN 1", true, null, List.of(), Set.of());
    when(auth.require(request)).thenReturn(actor);
    when(auth.token(request)).thenReturn("session-token");
    StoredFile image = new StoredFile(
        UUID.fromString("00000000-0000-0000-0000-000000000012"), null, "", "image",
        "pelvis.png", "images/pelvis.png", "image/png", 4L, "sha", mapper.createObjectNode(),
        7L, Instant.parse("2026-08-05T12:00:00Z"), "session", Instant.parse("2026-08-06T12:00:00Z"));
    when(files.storeImage(eq("pelvis.png"), any(byte[].class), eq("image/png"), eq("original"), eq(actor), eq("session-token")))
        .thenReturn(image);
    when(files.imageView(image)).thenReturn(Map.of(
        "url", "/api/media/images/pelvis.png", "name", "pelvis.png", "mime", "image/png", "size", 4L));
    HcopProperties properties = new HcopProperties(
        Path.of("runtime"), Path.of("catalog"), Path.of("storage"), "", "HCOP_SESSION", 480,
        1024L, 1024L, "qr", "encryption");
    StudyTemplateController controller = new StudyTemplateController(configurations, files, auth, mapper, properties);
    return new Fixture(controller, configurations, files, request, image);
  }

  private record Fixture(
      StudyTemplateController controller,
      ConfigurationService configurations,
      ClinicalFileService files,
      MockHttpServletRequest request,
      StoredFile image) {
  }
}
