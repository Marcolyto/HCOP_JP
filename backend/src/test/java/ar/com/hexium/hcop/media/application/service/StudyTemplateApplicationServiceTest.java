package ar.com.hexium.hcop.media.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.ConfigurationView;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.CreateCommand;
import ar.com.hexium.hcop.configuration.domain.ConfigurationDefinition;
import ar.com.hexium.hcop.media.application.port.in.ClinicalFileUseCase;
import ar.com.hexium.hcop.media.application.port.in.ClinicalFileUseCase.StoreImageCommand;
import ar.com.hexium.hcop.media.application.port.in.StudyTemplateUseCase.CreateStudyTemplateCommand;
import ar.com.hexium.hcop.media.application.port.out.StudyTemplateManifestStore;
import ar.com.hexium.hcop.media.domain.ClinicalFile;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class StudyTemplateApplicationServiceTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final StudyTemplateManifestStore manifest = mock(StudyTemplateManifestStore.class);
  private final ConfigurationManagementUseCase configurations = mock(ConfigurationManagementUseCase.class);
  private final ClinicalFileUseCase files = mock(ClinicalFileUseCase.class);
  private final StudyTemplateApplicationService service =
      new StudyTemplateApplicationService(manifest, configurations, files);
  private final ClinicalFile image = new ClinicalFile(
      UUID.fromString("00000000-0000-0000-0000-000000000012"), null, "", "image",
      "pelvis.png", "images/pelvis.png", "image/png", 4L, "sha", Map.of(),
      7L, Instant.parse("2026-08-05T12:00:00Z"), "session", Instant.parse("2026-08-06T12:00:00Z"));

  @Test
  void creaArchivoYConfiguracionCompletaEnUnaSolaOperacion() {
    when(files.storeImage(any(StoreImageCommand.class))).thenReturn(image);
    ConfigurationView created = view(12L, "Pelvis");
    when(configurations.create(any(CreateCommand.class))).thenReturn(created);

    var result = service.create(command(List.of("sagital", "femenina")));

    ArgumentCaptor<CreateCommand> command = ArgumentCaptor.forClass(CreateCommand.class);
    verify(configurations).create(command.capture());
    assertThat(command.getValue().kind()).isEqualTo("study-template");
    assertThat(command.getValue().actorId().value()).isEqualTo(7L);
    JsonNode definition = mapper.valueToTree(command.getValue().definition().value());
    assertThat(definition.path("attribution").asText()).isEqualTo("Equipo clínico");
    assertThat(definition.path("tags").size()).isEqualTo(2);
    assertThat(definition.path("tags").get(0).asText()).isEqualTo("sagital");
    assertThat(result.item()).isEqualTo(created);
    verify(files, never()).discardImage(image);
  }

  @Test
  void eliminaLaImagenSiNoPuedeCrearLaConfiguracion() {
    when(files.storeImage(any(StoreImageCommand.class))).thenReturn(image);
    IllegalStateException databaseFailure = new IllegalStateException("database unavailable");
    when(configurations.create(any(CreateCommand.class))).thenThrow(databaseFailure);

    assertThatThrownBy(() -> service.create(command(List.of())))
        .isSameAs(databaseFailure);

    verify(files).discardImage(image);
  }

  private CreateStudyTemplateCommand command(List<String> tags) {
    return new CreateStudyTemplateCommand(
        "Pelvis", "pelvis", tags, "Institución", "Equipo clínico", "Propia", "Descripción",
        "https://example.test/source", "https://example.test/license", true, "pelvis.png",
        new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47}, "image/png", UserId.of(7), "session-token");
  }

  private ConfigurationView view(long id, String name) {
    Instant now = Instant.parse("2026-08-05T12:00:00Z");
    return new ConfigurationView(
        String.valueOf(id), "study-template", "pelvis", name, "", true,
        ConfigurationDefinition.of(Map.of()), 1, now, now);
  }
}
